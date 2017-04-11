package org.jenkinsci.plugins.vcvarsall;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.Computer;
import hudson.model.TaskListener;
import hudson.remoting.Channel;
import hudson.slaves.ComputerListener;
import jenkins.security.MasterToSlaveCallable;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

/**
 * Listens for computers coming online and if they are configured to provide
 * MSVC compilers, runs the appropriate vcvarsall script on them.
 */
@Extension
public class ComputerListenerImpl extends ComputerListener {

    /**
     * Run vcvarsall on the agent
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
            if (!System.getProperty("os.name").startsWith("Windows")) {
                throw new RuntimeException("host is not running Windows");
            }
            String installKey = System.getenv("ProgramFiles(x86)") == null ?
                    "SOFTWARE\\Microsoft\\VisualStudio\\" + mVersion :
                    "SOFTWARE\\Wow6432Node\\Microsoft\\VisualStudio\\" + mVersion;

            try {
                return WinRegistry.readString(
                        WinRegistry.HKEY_LOCAL_MACHINE,
                        installKey,
                        "InstallRoot"
                );
            } catch (IllegalAccessException|InvocationTargetException e) {
                return "unknown";
            }
        }
    }

    @Override
    public void preOnline(Computer c, Channel channel, FilePath root, TaskListener listener) throws IOException, InterruptedException {
        super.preOnline(c, channel, root, listener);

        MsvcNodeProperty msvcNodeProperty = c.getNode().getNodeProperties().get(MsvcNodeProperty.class);
        if (msvcNodeProperty != null) {
            listener.getLogger().printf(
                    "running vcvarsall for %s (%s)\n",
                    msvcNodeProperty.getVersion(),
                    msvcNodeProperty.getArch()
            );
            Object v = channel.call(new RunVcvarsallCallable(
                    msvcNodeProperty.getVersion(),
                    msvcNodeProperty.getArch()
            ));
            listener.getLogger().printf("value: %s\n", (String) v);
        }
    }
}
