package org.jenkinsci.plugins.vcvarsall;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.Computer;
import hudson.model.TaskListener;
import hudson.remoting.Channel;
import hudson.slaves.ComputerListener;

import java.io.IOException;

/**
 * Listens for computers coming online and if they are configured to provide
 * MSVC compilers, runs the appropriate vcvarsall script on them.
 */
@Extension
public class ComputerListenerImpl extends ComputerListener {

    @Override
    public void preOnline(Computer c, Channel channel, FilePath root, TaskListener listener) throws IOException, InterruptedException {
        super.preOnline(c, channel, root, listener);

        //...
    }
}
