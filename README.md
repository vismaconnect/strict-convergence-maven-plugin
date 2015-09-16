# Strict Convergence Maven Plugin

This maven plugin checks whether the dependency graph of a module is convergent.
That is, that for every transitive dependency, the same version is used.

[![Build Status](https://api.travis-ci.org/EBPI/strict-convergence-maven-plugin.svg)](https://travis-ci.org/EBPI/strict-convergence-maven-plugin)

### Why should I care?

Every time there is an inconsistency in the dependency graph, one of the versions is chosen.
This means that some of the code has not been tested to work with that version.
This could result in nasty exceptions like NoSuchClassDefFoundError.
You may hope that these are caught by your tests, but you can't guarantee covering all the corner cases, especially in library code.
Inconsistent dependencies simply break Java's type safety.

### How is this different from the convergence rule of the enforcer plugin?

The reason for creating this plugin was that the enforcer plugin can not see through the dependencyManagement.
Indeed, a dependencyManagement section is supposed to override the versions of deeper dependencies, but having dependencyManagement mask the potential problems often means that some problems will remain undetected.
This plugin is more thorough, because it checks the dependencies that are actually used.

# Usage

In the `build/plugins` sections of your pom, add:

```xml
<plugin>
  <artifactId>ebpi-enforcer-maven-plugin</artifactId>
  <groupId>nl.ebpi.maven-plugins</groupId>
  <version>...</version>
</plugin>
```

When the plugin runs, it will print an error message describing the existing dependency problems.
Using this information, can try to find a set of dependencies that is consistent.
As a last resort, you may add `assumption`s.
Note that the `dependencyManagement` entry is necessary.

```xml
<dependencyManagement>
  <dependencies>
    ...
    <dependency>
      <groupId>org.scala-lang</groupId>
      <artifactId>scala-library</artifactId>
      <version>2.11.6</version>
    </dependency>
    ...
  </dependencies>
</dependencyManagement>
... <plugin>
      ...
      <configuration>
        <assumptions>
          <assumption>
            <artifact>
              <groupId>org.scala-lang</groupId>
              <artifactId>scala-library</artifactId>
              <version>2.11.6</version>
            </artifact>
            <isSufficientFor>2.11.0, 2.11.1, 2.11.2, 2.11.4</isSufficientFor>
          </assumption>
        </assumptions>
      </configuration>
...
```