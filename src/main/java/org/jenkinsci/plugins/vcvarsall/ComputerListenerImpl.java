package org.jenkinsci.plugins.vcvarsall;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Computer;
import hudson.model.TaskListener;
import hudson.remoting.Channel;
import hudson.slaves.ComputerListener;
import jenkins.security.MasterToSlaveCallable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.util.logging.Logger;

/**
 * Listens for computers coming online and if they are configured to provide
 * MSVC compilers, runs the appropriate vcvarsall script on them.
 */
@Extension
@SuppressWarnings("unused")
public class ComputerListenerImpl extends ComputerListener {

    /**
     * Run vcvarsall with the correct parameters
     */
    private static class RunVcvarsallCallable
            extends MasterToSlaveCallable<Object, RuntimeException> {

        private String mVersion;
        private String mArch;

        RunVcvarsallCallable(String version, String arch) {
            mVersion = version;
            mArch = arch;
        }

        @Override
        public Object call() throws RuntimeException {

            // Ensure the system is running Windows
            if (!System.getProperty("os.name").startsWith("Windows")) {
                throw new RuntimeException("host is not running Windows");
            }

            // Determine the actual architecture of the host
            boolean isHost32bit = System.getenv("ProgramFiles(x86)") == null;

            // Retrieve VS install directory
            String installKey = isHost32bit ?
                    "SOFTWARE\\Microsoft\\VisualStudio\\" + mVersion :
                    "SOFTWARE\\Wow6432Node\\Microsoft\\VisualStudio\\" + mVersion;
            String installDir;
            try {
                installDir = WinRegistry.readString(
                        WinRegistry.HKEY_LOCAL_MACHINE,
                        installKey,
                        "InstallDir"
                );
            } catch (IllegalAccessException|InvocationTargetException e) {
                throw new RuntimeException(e.getMessage());
            }
            if (installDir == null) {
                throw new RuntimeException("missing InstallDir registry key");
            }
            installDir += "\\..\\..\\VC\\vcvarsall.bat";

            // Determine the correct parameter to pass to vcvarsall
            String param = null;
            if (isHost32bit) {
                switch (mArch) {
                    case MsvcNodeProperty.X86:
                        param = "x86";
                        break;
                    case MsvcNodeProperty.X86_64:
                        param = "x86_amd64";
                        break;
                }
            } else {
                switch (mArch) {
                    case MsvcNodeProperty.X86:
                        param = "amd64_x86";
                        break;
                    case MsvcNodeProperty.X86_64:
                        param = "amd64";
                        break;
                }
            }
            if (param == null) {
                throw new RuntimeException("invalid arch parameter");
            }

            // Here's where things get clever - since the environment variables
            // will disappear as soon as cmd terminates, we print them all out
            // so that they can be used later when building
            EnvVars envVars = new EnvVars();
            try {
                Process process = new ProcessBuilder(
                        "cmd",
                        "/c",
                        "call",
                        installDir,
                        param,
                        "&&",
                        "set"
                ).start();
                BufferedReader reader=new BufferedReader(
                        new InputStreamReader(process.getInputStream())
                );
                String s;
                while ((s = reader.readLine()) != null) {
                    s = s.trim();
                    if (s.length() != 0) {
                        envVars.addLine(s);
                    }
                }
                process.waitFor();  // should have terminated by now
            } catch (IOException|InterruptedException e) {
                throw new RuntimeException(e.getMessage());
            }

            // Return the environment variables
            return envVars;
        }
    }

    @Override
    public void preOnline(Computer c, Channel channel, FilePath root, TaskListener listener) throws IOException, InterruptedException {
        super.preOnline(c, channel, root, listener);

        // Ignore any nodes that don't have the property
        MsvcNodeProperty msvcNodeProperty = c.getNode().getNodeProperties().get(MsvcNodeProperty.class);
        if (msvcNodeProperty == null) {
            return;
        }

        listener.getLogger().printf(
                "preparing to run vcvarsall for VS %s (%s)\n",
                msvcNodeProperty.getVersion(),
                msvcNodeProperty.getArch()
        );

        // Retrieve all of the environment variables from vcvarsall
        EnvVars envVars;
        try {
            envVars = (EnvVars) channel.call(new RunVcvarsallCallable(
                    msvcNodeProperty.getVersion(),
                    msvcNodeProperty.getArch()
            ));
        } catch (RuntimeException e) {
            listener.getLogger().println(e.getMessage());
            return;
        }

        // Pass the variables to the node
        msvcNodeProperty.setEnvVars(envVars);

        listener.getLogger().printf(
                "received %d env. variables from vcvarsall\n",
                envVars.size()
        );
    }
}
