# libs/

Put the Accurate Hitboxes jar for 1.20.1 here, named `accuratehitboxes-*.jar`. The build refuses to run without it.

It is only used at compile time (for the per-bone classifier) and is never bundled into Limb Damage. It's not
committed to this repository because it belongs to its authors: download it from their page.
The version used for development is Accurate Hitboxes 2.3.1.

If you update it, delete the old jar first. Two matching jars make the build fail on purpose rather than pick one.
