package net.suqatri.redicloud.node.listener;

import net.suqatri.redicloud.api.CloudAPI;
import net.suqatri.redicloud.api.event.CloudListener;
import net.suqatri.redicloud.api.node.ICloudNode;
import net.suqatri.redicloud.api.node.event.CloudNodeDisconnectEvent;
import net.suqatri.redicloud.api.redis.bucket.IRBucketHolder;
import net.suqatri.redicloud.node.NodeLauncher;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class CloudNodeDisconnectListener {

    @CloudListener
    public void onCloudNodeDisconnect(CloudNodeDisconnectEvent event) {
        event.getCloudNodeAsync()
                .whenComplete((nodeHolder, t) -> {
                    if (t != null) {
                        CloudAPI.getInstance().getConsole().error("Error while getting disconnected node information!", t);
                        return;
                    }
                    if (nodeHolder.get().getUniqueId().equals(NodeLauncher.getInstance().getNode().getUniqueId()))
                        return;
                    CloudAPI.getInstance().getConsole().info("Node %hc" + nodeHolder.get().getName() + " %tchas been disconnected from the cluster!");
                    if(event.getNodeId().equals(NodeLauncher.getInstance().getNode().getUniqueId())) return;
                    NodeLauncher.getInstance().getNodeManager().getNodesAsync()
                            .onFailure(t1 -> CloudAPI.getInstance().getConsole().error("Error while getting nodes!", t1))
                            .onSuccess(nodeHolders -> {
                               nodeHolders.removeIf(
                                       holder -> holder.get().getUniqueId().equals(NodeLauncher.getInstance().getNode().getUniqueId())
                                               || holder.get().getUniqueId().equals(event.getNodeId()));
                               Optional<IRBucketHolder<ICloudNode>> optional = nodeHolders.stream().findFirst();
                               if(optional.isPresent()){
                                   NodeLauncher.getInstance().getServiceManager().checkOldService(event.getNodeId());
                               }
                            });
                });
    }

}
