package net.suqatri.cloud.node.file.packet;

import lombok.Data;
import net.suqatri.cloud.api.CloudAPI;

import java.util.UUID;

@Data
public class FileTransferReadFailedPacket extends FileTransferPacket {

    private int indexesReceived;
    private int indexesSent;

    @Override
    public void receive() {
        CloudAPI.getInstance().getNodeManager().getNodeAsync(UUID.fromString(getPacketData().getSender().getIdentifier()))
            .onSuccess(nodeHolder -> {
                CloudAPI.getInstance().getConsole().error("§cNode §f" + nodeHolder.get().getName() + " §cfailed to read received file for transfer " + this.getTransferId() + "! (§f" + indexesReceived + "§c/§f" + indexesSent + "§c packets received)");
            });
    }

}