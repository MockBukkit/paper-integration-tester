## About

This is currently a project made to test MockBukkit against a real paper server. With further expansion it might be
possible to test plugins as well in integration tests, but this is not the main intended purpose.

## Requirements

- Maven central access
- Docker or podman installation on your machine
- JUnit or any testing framework

## How to use

With this library you will get a mirror implementation of the paper api, this mirror implementation is all generated
based on the paper api, where it aims to be an absolute copy. All the classnames will be the same, they will simply
just have moved to a different package. You should therefore be able to use this api to talk directly to a server

## Examples
A hypothetical testing setup using integration tester
```java
// Note that this has a different package name
import org.mockbukkit.integrationtester.bukkit.Bukkit; 
import org.mockbukkit.integrationtester.bukkit.Location;
import org.mockbukkit.integrationtester.bukkit.World;
import org.mockbukkit.integrationtester.bukkit.Material;
import org.mockbukkit.integrationtester.bukkit.entity.Player;
import java.util.UUID;

class MyTestCase {

    @Test
    void aTest() {
        try (PaperIntegrationTester integrationTester = new PaperIntegrationTester(MyPlugin.class)) {
            Server server = Bukkit.getServer();
            // Do whatever you want with the server, as you could with a normal plugin
            integrationTester.advanceOneTick();
            assertTrues(server.getPluginManager().isPluginEnabled("MyPlugin"));
        }
    }
    
    @Test
    void aPlayerTest(){
        // Players are not available to initialize using the paper api, this project adds an option for that
        try (PaperIntegrationTester integrationTester = new PaperIntegrationTester(MyPlugin.class)) {
            // With random uuid
            Pair<Player,PlayerClient> notch = integrationTester.addPlayer("notch");
            // With predefined uuid
            Pair<Player,PlayerClient> thorinwasher = integrationTester.addPlayer("thorinwasher", UUID.randomUUID());
            Location location = new Location(Bukkit.getServer().getWorlds()[0], x, y, z);
            notch.first().teleport(location); // Teleport the player on the serverside
            notch.second().removeBlock(location); // This will await until the action has been completed
            assertEquals(Material.AIR, location.getBlock().getType());
        }
    }
}
```

## For developers

To test code gen, simply run this command: 
```
./gradlew code-generator:run
```


