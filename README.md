# pumlink-maven-plugin
A Maven plugin validating a PlantUML definition against the project dependencies.

1. Start by adding the dependency. "Installing" the plugin locally should be enough to start:
```
cd pumlink-maven-plugin
mvn clean install 
```
2. Go to the project/module of your choice and create (empty) project.puml files in the root of the main module and all 
submodules.
3. Run the plugin from the chosen module:
``` 
mvn tech.pumlink:pumlink-maven-plugin:0.0.1-SNAPSHOT:pumlink
```
You should see something like:
```
org.apache.maven.lifecycle.LifecycleExecutionException: 
Failed to execute goal tech.pumlink:pumlink-maven-plugin:0.0.1-SNAPSHOT:pumlink (default-cli) on project module-name: 
Execution default-cli of goal tech.pumlink:pumlink-maven-plugin:0.0.1-SNAPSHOT:pumlink failed: 
PlantUML not in sync with actual project state!
```