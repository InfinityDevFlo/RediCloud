package net.suqatri.redicloud.node.poll.timeout;

import net.suqatri.redicloud.api.CloudAPI;
import net.suqatri.redicloud.api.impl.poll.timeout.ITimeOutPoll;
import net.suqatri.redicloud.api.impl.poll.timeout.ITimeOutPollManager;
import net.suqatri.redicloud.api.impl.poll.timeout.TimeOutResult;
import net.suqatri.redicloud.api.impl.redis.bucket.RedissonBucketManager;
import net.suqatri.redicloud.api.network.NetworkComponentType;
import net.suqatri.redicloud.api.node.ICloudNode;
import net.suqatri.redicloud.api.redis.bucket.IRBucketHolder;
import net.suqatri.redicloud.api.redis.event.RedisConnectedEvent;
import net.suqatri.redicloud.api.redis.event.RedisDisconnectedEvent;
import net.suqatri.redicloud.api.scheduler.IRepeatScheduler;
import net.suqatri.redicloud.commons.function.future.FutureAction;
import net.suqatri.redicloud.node.NodeLauncher;
import net.suqatri.redicloud.node.node.packet.NodePingPacket;
import net.suqatri.redicloud.node.poll.timeout.packet.TimeOutPollRequestPacket;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class TimeOutPollManager extends RedissonBucketManager<TimeOutPoll, ITimeOutPoll> implements ITimeOutPollManager {

    private IRepeatScheduler<?> task;

    public TimeOutPollManager() {
        super("timeouts", ITimeOutPoll.class);
        CloudAPI.getInstance().getEventManager().register(RedisConnectedEvent.class, event -> this.checker());
    }

    private void checker() {
        CloudAPI.getInstance().getEventManager().register(RedisDisconnectedEvent.class, event -> this.task.cancel());
        this.task = CloudAPI.getInstance().getScheduler().scheduleTaskAsync(() -> {
            if (NodeLauncher.getInstance().isRestarting()
                    || NodeLauncher.getInstance().isShutdownInitialized()
                    || NodeLauncher.getInstance().isInstanceTimeOuted()) {
                this.task.cancel();
                return;
            }
            CloudAPI.getInstance().getNodeManager().getNodesAsync()
                    .onFailure(e -> CloudAPI.getInstance().getConsole().error("Error while checking timeouts", e))
                    .onSuccess(nodes -> {
                        nodes.forEach(node -> {
                            if (!node.get().isConnected()) return;
                            if (node.get().getUniqueId().equals(NodeLauncher.getInstance().getNode().getUniqueId()))
                                return;
                            NodePingPacket packet = new NodePingPacket();
                            packet.getPacketData().addReceiver(node.get().getNetworkComponentInfo());
                            packet.getPacketData().waitForResponse()
                                    .orTimeout(TimeOutPoll.PACKET_RESPONSE_TIMEOUT, TimeUnit.MILLISECONDS)
                                    .onFailure(e -> {
                                        if (e.getCause() instanceof TimeoutException) {
                                            TimeOutPoll poll = new TimeOutPoll();
                                            poll.setPollId(UUID.randomUUID());
                                            poll.setOpenerId(NodeLauncher.getInstance().getNode().getUniqueId());
                                            poll.setTimeOutTargetId(node.get().getUniqueId());
                                            this.createPoll(poll)
                                                    .onFailure(e1 -> CloudAPI.getInstance().getConsole().error("Error while creating timeout poll", e1))
                                                    .onSuccess(pollHolder -> {
                                                        for (IRBucketHolder<ICloudNode> nodeHolder : nodes) {
                                                            if (!nodeHolder.get().isConnected()) continue;
                                                            if (nodeHolder.get().getUniqueId().equals(pollHolder.get().getTimeOutTargetId()))
                                                                continue;
                                                            pollHolder.get().getResults().put(nodeHolder.get().getUniqueId(),
                                                                    pollHolder.get().getOpenerId().equals(nodeHolder.get().getUniqueId()) ? TimeOutResult.FAILED : TimeOutResult.UNKNOWN);
                                                        }
                                                        TimeOutPollRequestPacket requestPacket = new TimeOutPollRequestPacket();
                                                        requestPacket.setPollId(pollHolder.get().getPollId());
                                                        requestPacket.publishAllAsync(NetworkComponentType.NODE);
                                                        CloudAPI.getInstance().getScheduler().runTaskLaterAsync(poll::close,
                                                                TimeOutPoll.PACKET_RESPONSE_TIMEOUT + 1500, TimeUnit.MILLISECONDS);
                                                    });
                                            return;
                                        }
                                        CloudAPI.getInstance().getConsole().error("Error while checking timeouts", e);
                                    });
                            packet.publishAsync();
                        });
                    });
        }, 15, 15, TimeUnit.SECONDS);
    }

    @Override
    public FutureAction<IRBucketHolder<ITimeOutPoll>> createPoll(ITimeOutPoll timeOutPool) {
        return createBucketAsync(timeOutPool.getPollId().toString(), timeOutPool);
    }

    @Override
    public FutureAction<IRBucketHolder<ITimeOutPoll>> getPoll(UUID pollId) {
        return this.getBucketHolderAsync(pollId.toString());
    }

    @Override
    public FutureAction<Boolean> closePoll(IRBucketHolder<ITimeOutPoll> poolHolder) {
        return this.deleteBucketAsync(poolHolder.getIdentifier());
    }
}
