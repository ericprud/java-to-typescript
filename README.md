Java to Typescript
---
Converts Fortran to Cobol.

This code was written to reduce RSI associated with porting code from Java to Typescript; those repetative tasks like tweaking imports, moving types around, etc. It's not likely to be complete this century, but it doesn't have to be complete to help in porting.

## Contributing

To play with this, clone the accompanying test java package: https://github.com/ericprud/javatots-test as a sybling of this repo. Then you should be able to run the main without command line args and reproduce the .ts files under `../javatots-test/asTypescript/packages`.
```shell
(cd javatots &&
 java org.javatots.main.JavaToTypescript)
```
Once you run this, you can
```shell
(cd ../javatots-test/asTypescript/src/packages/ &&
 for d in custdb custapp;
   cd $d &&
   npm run build)
```
You should be able to do that from `../javatots-test/asTypescript/` but I guess node's script runner doesn't understand `for f in foo*; do`.

Any typescript errors you eliminate are a step up.


## Plan

1. Plug-in architecture to map invocations of java libraries to analogous javascript libraries.
2. Round out more Lombok features.
3. Keep picking away at Typescript errors.
4. Parse tsconfig to control whether to add `this.` qualifiers to member variables and functions.
