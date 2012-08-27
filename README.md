# Scalaboot

## Description

Scalaboot is a template project to seed new scala repository. It includes the base framework
NRV and test libs as dependencies.

## Quick start

- git clone git@github.com:wajam/scalaboot.git
- Change the name of ScalabootBuild object and PROJECT_NAME val in /project/Project.scala to the name of your project.
- Add project dependencies, if any, to project/Project.scala
- Change the name of the scalaboot-core folder to reflect your project name.
- Change the package and the name of the application class.
- Install sbt (https://github.com/harrah/xsbt/wiki).
- sbt clean compile stage : this will compile and create a start script for your app.

