## vcvarsall Plugin

This plugin adds an option to run `vcvarsall.bat` on Windows nodes upon connect.

### Screenshot

![Configuration options](https://i.stack.imgur.com/NY7QO.png)

### How It Works

When a node connects, the plugin performs the following actions:

- Consults the registry to determine the installation path to Visual Studio
- Uses the installation path to find the `vcvarsall.bat` script
- Executes the script and records the environment variables it sets

Then, whenever a build runs on that node, the environment variables that were captured earlier are set.
