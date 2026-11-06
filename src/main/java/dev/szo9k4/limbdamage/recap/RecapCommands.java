package dev.szo9k4.limbdamage.recap;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.szo9k4.limbdamage.BodyPart;
import dev.szo9k4.limbdamage.BodyPartClassifier;
import dev.szo9k4.limbdamage.HitLocationTracker;
import dev.szo9k4.limbdamage.Limb;
import dev.szo9k4.limbdamage.LimbDamageMod;
import dev.szo9k4.limbdamage.compat.TaczCompat;
import dev.szo9k4.limbdamage.config.CategoryConfig;
import dev.szo9k4.limbdamage.config.LimbConfig;
import dev.szo9k4.limbdamage.config.Multipliers;
import dev.szo9k4.limbdamage.dummy.DummyEntity;
import dev.szo9k4.limbdamage.dummy.ModEntities;
import dev.szo9k4.limbdamage.effect.ModEffects;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Test commands (op level 2):
 *   /limbrecap test [attackers]      show a fake recap with random attackers / limbs (UI check)
 *   /limbrecap testself             fake self-inflicted recap
 *   /limbrecap hit <limb|random> [damage]
 *        limb = head, chest, abdomen, left_arm, right_arm, left_leg, right_leg, arms, legs, random.
 *        A REAL hit on yourself through the whole pipeline (multiplier, armor, recap log); prints the
 *        multiplier and the health actually lost. Lethal if the damage is high enough.
 *   /limbrecap probe                prints how the hit zones read on YOUR hitbox (front face, height x
 *                                   left/right offset), to verify head/chest/abdomen/arms/legs borders.
 */
@Mod.EventBusSubscriber(modid = LimbDamageMod.MOD_ID)
public final class RecapCommands {

    private static final Random RANDOM = new Random();
    private static final String[] NAMES = {"Firearm Merchant", "Zombie", "Skeleton", "Pillager", "Steve", "Alex", "Creeper"};
    private static final List<String> PART_NAMES = List.of("head", "body", "abdomen", "arms", "legs", "feet");
    private static final List<String> SCOPES = scopes();
    private static final List<String> LIMB_NAMES = List.of("head", "chest", "abdomen", "left_arm", "right_arm",
            "left_leg", "right_leg", "left_foot", "right_foot", "arms", "legs", "feet", "random");

    private RecapCommands() {
    }

    private static List<String> scopes() {
        List<String> all = new java.util.ArrayList<>();
        all.add("global");
        all.addAll(CategoryConfig.CATEGORIES);
        return List.copyOf(all);
    }

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        LiteralCommandNode<CommandSourceStack> root = event.getDispatcher().register(Commands.literal("limbrecap")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("test")
                        .executes(c -> test(c, 2, false))
                        .then(Commands.argument("attackers", IntegerArgumentType.integer(1, 6))
                                .executes(c -> test(c, IntegerArgumentType.getInteger(c, "attackers"), false))))
                .then(Commands.literal("testself").executes(c -> test(c, 1, true)))
                .then(Commands.literal("hit")
                        .then(Commands.argument("limb", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(LIMB_NAMES, b))
                                .executes(c -> hit(c, StringArgumentType.getString(c, "limb"), 5f))
                                .then(Commands.argument("damage", FloatArgumentType.floatArg(0.1f, 1000f))
                                        .executes(c -> hit(c, StringArgumentType.getString(c, "limb"),
                                                FloatArgumentType.getFloat(c, "damage"))))))
                .then(Commands.literal("probe").executes(RecapCommands::probe))
                .then(Commands.literal("testreport").executes(RecapCommands::testReport))
                .then(Commands.literal("testdealt").executes(RecapCommands::testDealt))
                .then(Commands.literal("multipliers").executes(RecapCommands::multipliers))
                .then(Commands.literal("reload").executes(c -> {
                    LimbConfig.load();
                    say(c.getSource(), "\u00a7a[LimbDamage] gameplay config reloaded from limbdamage-common.json5");
                    return 1;
                }))
                .then(Commands.literal("setmult")
                        .then(Commands.argument("scope", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(SCOPES, b))
                                .then(Commands.argument("part", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(PART_NAMES, b))
                                        .then(Commands.argument("value", StringArgumentType.word())
                                                .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("inherit", "1.0"), b))
                                                .executes(c -> setMult(c, StringArgumentType.getString(c, "scope"),
                                                        StringArgumentType.getString(c, "part"),
                                                        StringArgumentType.getString(c, "value")))))))
                .then(Commands.literal("dummy")
                        .executes(c -> dummy(c, -1f))
                        .then(Commands.literal("clear").executes(RecapCommands::dummyClear))
                        .then(Commands.argument("health", FloatArgumentType.floatArg(1f, 1024f))
                                .executes(c -> dummy(c, FloatArgumentType.getFloat(c, "health"))))));
        // /limbdamage is the same command tree under the mod's real name
        event.getDispatcher().register(Commands.literal("limbdamage").requires(src -> src.hasPermission(2)).redirect(root));
    }

    // ------------------------------------------------------------------ test

    private static int test(CommandContext<CommandSourceStack> ctx, int attackers, boolean self)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ItemStack held = player.getMainHandItem();
        ItemStack[] pool = {new ItemStack(Items.IRON_SWORD), new ItemStack(Items.BOW),
                new ItemStack(Items.TNT), new ItemStack(Items.DIAMOND_AXE), new ItemStack(Items.TRIDENT)};

        List<RecapPacket.RowData> rows = new java.util.ArrayList<>();
        float[] parts = new float[Limb.values().length];
        for (int i = 0; i < attackers; i++) {
            ItemStack weapon = (i == 0 && !held.isEmpty()) ? held.copy() : pool[RANDOM.nextInt(pool.length)].copy();
            weapon.setCount(1);
            float damage = 5f + RANDOM.nextFloat() * 40f;
            int hits = 1 + RANDOM.nextInt(8);
            String name = self ? player.getName().getString() : NAMES[RANDOM.nextInt(NAMES.length)];
            rows.add(new RecapPacket.RowData(name, !self && RANDOM.nextBoolean(), self, weapon, damage, hits, "", false));
            // spread this attacker's damage over 1-2 random limbs
            int limbs = 1 + RANDOM.nextInt(2);
            for (int k = 0; k < limbs; k++) {
                parts[RANDOM.nextInt(parts.length)] += damage / limbs;
            }
        }
        rows.sort((a, b) -> Float.compare(b.damage(), a.damage()));
        RecapNetwork.sendTo(player, new RecapPacket(player.getMaxHealth(), parts, attackers, rows, false));
        ctx.getSource().sendSuccess(() -> Component.literal("[LimbRecap] sent a fake recap"
                + (held.isEmpty() ? " (hold a TACZ gun to test its HUD icon)" : "")), false);
        return 1;
    }

    /** A fake death recap with the "you dealt" toast under it (one KILL, one plain), to check the layout. */
    private static int testDealt(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        test(ctx, 2, false); // the main toast the second one stacks under
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ItemStack weapon = player.getMainHandItem().isEmpty() ? new ItemStack(Items.IRON_SWORD) : player.getMainHandItem().copy();
        weapon.setCount(1);
        float[] parts = new float[Limb.values().length];
        parts[Limb.CHEST.ordinal()] = 14.5f;
        parts[Limb.LEFT_ARM.ordinal()] = 6.0f;
        parts[Limb.HEAD.ordinal()] = 5.25f;
        List<RecapPacket.RowData> rows = new java.util.ArrayList<>();
        rows.add(new RecapPacket.RowData("Steve", true, false, weapon, 20.5f, 3, "KILL", true));
        rows.add(new RecapPacket.RowData("Alex", true, false, weapon, 5.25f, 1, "", false));
        RecapNetwork.sendTo(player, new RecapPacket(20f, parts, rows.size(), rows, false, true));
        ctx.getSource().sendSuccess(() -> Component.literal("[LimbRecap] sent a fake 'you dealt' toast under the recap"), false);
        return 1;
    }

    private static int testReport(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ItemStack held = player.getMainHandItem().isEmpty() ? new ItemStack(Items.IRON_SWORD) : player.getMainHandItem().copy();
        held.setCount(1);
        Limb[] limbs = Limb.values();
        double[] mults = {0.8, 1.0, 1.0, 1.5, 2.5};
        List<RecapPacket.RowData> rows = new java.util.ArrayList<>();
        float[] parts = new float[limbs.length];
        for (int i = 0; i < 10; i++) { // 10 hits: also shows the "+N more" collapse
            Limb limb = limbs[RANDOM.nextInt(limbs.length)];
            double mult = mults[RANDOM.nextInt(mults.length)];
            float damage = (float) (4.0 + RANDOM.nextDouble() * 8.0) * (float) mult;
            boolean special = mult > 2.0;
            String tag = String.format(Locale.ROOT, "x%.2f", mult) + (special ? " SNP" : "");
            parts[limb.ordinal()] += damage;
            rows.add(new RecapPacket.RowData(limb.label(), false, false, held, damage, 1, tag, special));
        }
        RecapNetwork.sendTo(player, new RecapPacket(20f, parts, rows.size(), rows, true));
        ctx.getSource().sendSuccess(() -> Component.literal("[LimbRecap] sent a fake dummy report (10 hits)"), false);
        return 1;
    }

    // ------------------------------------------------------------------ multipliers

    private static void say(CommandSourceStack src, String text) {
        src.sendSuccess(() -> Component.literal(text), false);
    }

    private static String fmt2(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private static BodyPart parsePart(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "head" -> BodyPart.HEAD;
            case "body", "chest" -> BodyPart.BODY;
            case "abdomen", "stomach" -> BodyPart.ABDOMEN;
            case "arms", "arm" -> BodyPart.ARMS;
            case "legs", "leg" -> BodyPart.LEGS;
            case "feet", "foot" -> BodyPart.FEET;
            default -> null;
        };
    }

    /** What is really in effect: the global values, each TACZ category (value set = replaces global,
     *  inherited = global shows through), and whether the TACZ headshot override is live. */
    private static int multipliers(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        say(src, "\u00a76[LimbDamage] multipliers in effect    \u00a7bcyan = category value (REPLACES global)    \u00a77grey = inherits global");

        StringBuilder global = new StringBuilder("\u00a7fGLOBAL   ");
        for (BodyPart p : BodyPart.values()) {
            global.append(p.name().toLowerCase(Locale.ROOT)).append(' ').append(fmt2(Multipliers.global(p))).append("   ");
        }
        say(src, global.toString());

        if (!CategoryConfig.ENABLED.get()) {
            say(src, "\u00a7cPer-category multipliers are DISABLED (weaponCategories.enabled = false): every TACZ hit uses GLOBAL.");
        } else {
            for (String category : CategoryConfig.CATEGORIES) {
                StringBuilder line = new StringBuilder("\u00a7f").append(category.toUpperCase(Locale.ROOT)).append("   ");
                boolean any = false;
                for (BodyPart p : BodyPart.values()) {
                    double own = CategoryConfig.get(category, p);
                    String name = p.name().toLowerCase(Locale.ROOT);
                    if (own >= 0.0) {
                        any = true;
                        line.append("\u00a7b").append(name).append(' ').append(fmt2(own)).append("   ");
                    } else {
                        line.append("\u00a77").append(name).append(' ').append(fmt2(Multipliers.global(p))).append("   ");
                    }
                }
                if (!any) {
                    line.append("\u00a78(nothing set: all inherit global)");
                }
                say(src, line.toString());
            }
        }

        boolean available = TaczCompat.headshotOverrideAvailable();
        if (CategoryConfig.HEADSHOT_OVERRIDE.get()) {
            say(src, available
                    ? "\u00a7aTACZ headshot override: ON. A headshot = base damage x the head value above (TACZ's own headshot multiplier is cancelled)."
                    : "\u00a7cTACZ headshot override: ON but NOT AVAILABLE in this TACZ build, so TACZ's own headshot multiplier still applies ON TOP of the head value.");
        } else {
            say(src, "\u00a77TACZ headshot override: OFF. A headshot = TACZ's own headshot multiplier, then the head value above on top of it.");
        }
        say(src, "\u00a77headshotGuard: " + (CategoryConfig.HEADSHOT_GUARD.get() ? (available ? "ON" : "ON (but TACZ accessors unavailable)") : "OFF")
                + "   |   Fragile effect: arms/legs/feet always x1.00   |   non-TACZ damage always uses GLOBAL");
        return 1;
    }

    /** /limbdamage setmult <global|category> <part> <value|inherit>: edits the config and saves it. */
    private static int setMult(CommandContext<CommandSourceStack> ctx, String scope, String partName, String valueText) {
        CommandSourceStack src = ctx.getSource();
        BodyPart part = parsePart(partName);
        if (part == null) {
            src.sendFailure(Component.literal("Unknown part. Use: " + String.join(", ", PART_NAMES)));
            return 0;
        }
        String key = scope.toLowerCase(Locale.ROOT);
        boolean global = key.equals("global");
        if (!global && !CategoryConfig.exists(key)) {
            src.sendFailure(Component.literal("Unknown scope. Use: " + String.join(", ", SCOPES)));
            return 0;
        }
        double value;
        if (valueText.equalsIgnoreCase("inherit") || valueText.equalsIgnoreCase("reset")) {
            if (global) {
                src.sendFailure(Component.literal("'global' values can't inherit; give a number (0 - 10)."));
                return 0;
            }
            value = -1.0;
        } else {
            try {
                value = Double.parseDouble(valueText);
            } catch (NumberFormatException e) {
                src.sendFailure(Component.literal("Value must be a number or 'inherit'."));
                return 0;
            }
        }
        if (value < (global ? 0.0 : -1.0) || value > 10.0) {
            src.sendFailure(Component.literal("Value out of range (" + (global ? "0" : "-1 / inherit, or 0") + " - 10)."));
            return 0;
        }
        if (global) {
            Multipliers.setGlobal(part, value);
        } else {
            CategoryConfig.set(key, part, value);
            LimbConfig.save();
        }
        say(src, "\u00a7a[LimbDamage] " + key + " / " + part.name().toLowerCase(Locale.ROOT) + " = "
                + (value < 0 ? "inherit (uses global " + fmt2(Multipliers.global(part)) + ")" : fmt2(value))
                + "   \u00a77(saved to the config)");
        return 1;
    }

    // ------------------------------------------------------------------ hit

    private static Limb parseLimb(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "head" -> Limb.HEAD;
            case "chest", "body" -> Limb.CHEST;
            case "abdomen", "stomach" -> Limb.ABDOMEN;
            case "left_arm" -> Limb.LEFT_ARM;
            case "right_arm" -> Limb.RIGHT_ARM;
            case "left_leg" -> Limb.LEFT_LEG;
            case "right_leg" -> Limb.RIGHT_LEG;
            case "arms" -> RANDOM.nextBoolean() ? Limb.LEFT_ARM : Limb.RIGHT_ARM;
            case "legs" -> RANDOM.nextBoolean() ? Limb.LEFT_LEG : Limb.RIGHT_LEG;
            case "left_foot" -> Limb.LEFT_FOOT;
            case "right_foot" -> Limb.RIGHT_FOOT;
            case "feet" -> RANDOM.nextBoolean() ? Limb.LEFT_FOOT : Limb.RIGHT_FOOT;
            case "random" -> Limb.values()[RANDOM.nextInt(Limb.values().length)];
            default -> null;
        };
    }

    private static int hit(CommandContext<CommandSourceStack> ctx, String limbName, float damage)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Limb limb = parseLimb(limbName);
        if (limb == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown limb. Use: " + String.join(", ", LIMB_NAMES)));
            return 0;
        }

        // Synthetic hit on the middle of that limb: {height (blocks, 1.8 player), lateral (+ = left)}.
        double[] spec = switch (limb) {
            case HEAD -> new double[]{1.62, 0.0};
            case CHEST -> new double[]{1.25, 0.0};
            case ABDOMEN -> new double[]{0.88, 0.0};
            case LEFT_ARM -> new double[]{1.05, 0.40};
            case RIGHT_ARM -> new double[]{1.05, -0.40};
            case LEFT_LEG -> new double[]{0.35, 0.12};
            case RIGHT_LEG -> new double[]{0.35, -0.12};
            case LEFT_FOOT -> new double[]{0.08, 0.12};
            case RIGHT_FOOT -> new double[]{0.08, -0.12};
        };
        AABB box = player.getBoundingBox();
        double yaw = Math.toRadians(player.yBodyRot);
        Vec3 pos = new Vec3(box.getCenter().x + Math.cos(yaw) * spec[1],
                box.minY + spec[0] * (box.getYsize() / 1.8),
                box.getCenter().z + Math.sin(yaw) * spec[1]);

        HitLocationTracker.record(player, pos, limb.bodyPart(), spec[1]);
        float before = player.getHealth() + player.getAbsorptionAmount();
        player.invulnerableTime = 0;
        boolean hurt = player.hurt(player.damageSources().playerAttack(player), damage);
        float lost = before - (player.getHealth() + player.getAbsorptionAmount());

        BodyPart bp = limb.bodyPart();
        double mult = BodyPartClassifier.multiplier(bp);
        if ((bp == BodyPart.ARMS || bp == BodyPart.LEGS || bp == BodyPart.FEET) && player.hasEffect(ModEffects.FRAGILE.get())) {
            mult = 1.0;
        }
        final double shownMult = mult;
        ctx.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "[LimbRecap] %s hit: base %.1f x%.2f -> health lost %.1f%s",
                limb, damage, shownMult, lost, hurt ? "" : " (hurt() returned false: creative / invulnerable?)")), false);
        return 1;
    }

    // ------------------------------------------------------------------ dummy

    private static int dummy(CommandContext<CommandSourceStack> ctx, float health) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        DummyEntity dummy = ModEntities.DUMMY.get().create(level);
        if (dummy == null) {
            ctx.getSource().sendFailure(Component.literal("Could not create the dummy."));
            return 0;
        }
        double yawRad = Math.toRadians(player.getYRot());
        float yaw = player.getYRot() + 180f; // faces the player
        dummy.moveTo(player.getX() - Math.sin(yawRad) * 2.5, player.getY(), player.getZ() + Math.cos(yawRad) * 2.5, yaw, 0f);
        dummy.setYBodyRot(yaw);
        dummy.setYHeadRot(yaw);
        dummy.setOwnerUuid(player.getUUID());
        float hp = health > 0f ? health : LimbConfig.DUMMY_HEALTH.get().floatValue();
        dummy.configureHealth(hp);
        level.addFreshEntity(dummy);
        ctx.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "[LimbRecap] spawned a training dummy (%.0f health) in front of you. Hit it: your action bar shows limb, multiplier and damage.", hp)), false);
        return 1;
    }

    private static int dummyClear(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerLevel level = ctx.getSource().getPlayerOrException().serverLevel();
        int removed = 0;
        for (DummyEntity dummy : level.getEntities(EntityTypeTest.forClass(DummyEntity.class), d -> true)) {
            dummy.discard();
            removed++;
        }
        final int count = removed;
        ctx.getSource().sendSuccess(() -> Component.literal("[LimbRecap] removed " + count + " dummies"), false);
        return count;
    }

    // ------------------------------------------------------------------ probe

    private static String abbreviation(Limb limb) {
        return switch (limb) {
            case HEAD -> "HEAD";
            case CHEST -> "CHEST";
            case ABDOMEN -> "ABD";
            case LEFT_ARM -> "L_ARM";
            case RIGHT_ARM -> "R_ARM";
            case LEFT_LEG -> "L_LEG";
            case RIGHT_LEG -> "R_LEG";
            case LEFT_FOOT -> "L_FOOT";
            case RIGHT_FOOT -> "R_FOOT";
        };
    }

    private static int probe(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        AABB box = player.getBoundingBox();
        double yaw = Math.toRadians(player.yBodyRot);
        double lx = Math.cos(yaw), lz = Math.sin(yaw);   // your LEFT
        double fx = -lz, fz = lx;                        // your FORWARD
        double cx = box.getCenter().x, cz = box.getCenter().z;
        double scale = box.getYsize() / 1.8;
        double[] heights = {1.78, 1.55, 1.40, 1.25, 1.10, 0.95, 0.80, 0.65, 0.45, 0.30, 0.10};
        double[] laterals = {-0.28, -0.15, 0.0, 0.15, 0.28};

        CommandSourceStack src = ctx.getSource();
        src.sendSuccess(() -> Component.literal("[LimbRecap] horizontal shots from the front (columns: lateral -.28 -.15 0 +.15 +.28; + = your LEFT):"), false);
        for (double h : heights) {
            StringBuilder line = new StringBuilder(String.format(Locale.ROOT, "h=%.2f  ", h));
            for (double lat : laterals) {
                Vec3 p = new Vec3(cx + lx * lat + fx * 0.3, box.minY + h * scale, cz + lz * lat + fz * 0.3);
                // a horizontal shot from the front through that point, like a real bullet
                Vec3 from = p.add(fx, 0.0, fz);
                Vec3 to = p.subtract(fx, 0.0, fz);
                line.append(String.format(Locale.ROOT, "%-7s", abbreviation(BodyPartClassifier.classifyLimbRay(player, p, from, to))));
            }
            String text = line.toString();
            src.sendSuccess(() -> Component.literal(text), false);
        }
        src.sendSuccess(() -> Component.literal("(player-model ray test; Accurate Hitboxes bone verdicts are not shown here)"), false);
        return 1;
    }
}
