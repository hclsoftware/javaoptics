# Optics for Java with change

* Lens
* Optional
* Iso
* Traversal
* Fold

# Annotation Processor that automatically generates the optics for simple records
Not needed to use the library, but useful for generating the optics for simple records.

## @Optic annotation

This can be added to any record. It will

* Create a companion object with optics for each field
    * Lens for each field
    * Traversal for each field that is a list/collection or stream
* If addListTraversal is true, then a traversal is added that traverses over a list of the record type
    * This is useful when business methods have a list of the records
* The 'traversals' field allows chains of traversals to be generated in the companion object
    * See the example below for examples.
    * Note that a traversal chain can be given a name as shown in the second array element in the example below.

## Example

```java

@Optics(debug = false, addListTraversal = true, traversals = {
        "commandeTransportList.tronconTransportList",
        "commande2ChassisT:commandeTransportList.tronconTransportList.chassisTronconList"
})
public record Commande(List<CommandeTransport> commandeTransportList) {
}
```

This generated the companion object:

```java
public interface CommandeOptics extends IGeneratedOptics<Commande> {
    ILens<Commande, List<CommandeTransport>> commandeTransportListL =
            ILens.of(Commande::commandeTransportList, (main, value) -> new Commande(value));

    ITraversal<Commande, CommandeTransport> commandeTransportListT =
            ITraversal.fromListLens(commandeTransportListL);

    ITraversal<List<Commande>, Commande> listT = ITraversal.listTraversal();
    ITraversal<Commande, TronconTransport> commandeTransportList_tronconTransportListT = CommandeOptics.commandeTransportListT
            .andThen(CommandeTransportOptics.tronconTransportListT);

    ITraversal<Commande, ChassisTroncon> commande2ChassisT = CommandeOptics.commandeTransportListT
            .andThen(CommandeTransportOptics.tronconTransportListT)
            .andThen(TronconTransportOptics.chassisTronconListT);
}
``` 

# Known issues / Work in progress

The automatic generated @Optics doesn't work on classes with generics yet

For example the following DOES NOT WORK

```java

@Optics //does not work yet 
public record Tuple<A, B>(A a, B b) {
}
```

# To release

```shell
mvn versions:set "-DnewVersion=x.x.x"
mvn clean deploy -P release
mvn versions:set "-DnewVersion=x.y.y-SNAPSHOT"
```

