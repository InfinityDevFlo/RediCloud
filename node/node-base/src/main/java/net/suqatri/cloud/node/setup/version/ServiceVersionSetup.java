package net.suqatri.cloud.node.setup.version;

import lombok.Getter;
import net.suqatri.cloud.api.service.ServiceEnvironment;
import net.suqatri.cloud.node.NodeLauncher;
import net.suqatri.cloud.node.console.setup.Setup;
import net.suqatri.cloud.node.console.setup.SetupHeaderBehaviour;
import net.suqatri.cloud.node.console.setup.annotations.Question;
import net.suqatri.cloud.node.console.setup.annotations.RequiresEnum;

@Getter
public class ServiceVersionSetup extends Setup<ServiceVersionSetup> {

    @Question(id = 1, question = "What is the environment type of this version?")
    @RequiresEnum(ServiceEnvironment.class)
    private ServiceEnvironment environment;

    @Question(id = 2, question = "What is the download url of this version?")
    private String downloadUrl;

    @Question(id = 3, question = "Is this version a paper clip?")
    private boolean paperClip;

    public ServiceVersionSetup() {
        super(NodeLauncher.getInstance().getConsole());
    }

    @Override
    public boolean isCancellable() {
        return true;
    }

    @Override
    public boolean shouldPrintHeader() {
        return true;
    }

    @Override
    public SetupHeaderBehaviour headerBehaviour() {
        return SetupHeaderBehaviour.RESTORE_PREVIOUS_LINES;
    }
}
