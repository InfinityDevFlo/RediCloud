package net.suqatri.cloud.api.service;

import net.suqatri.cloud.api.group.ICloudGroup;
import net.suqatri.cloud.api.redis.bucket.IRBucketHolder;
import net.suqatri.cloud.api.redis.bucket.IRBucketObject;
import net.suqatri.cloud.api.service.configuration.IServiceStartConfiguration;

import java.util.UUID;

public interface ICloudService extends IRBucketObject {

    IServiceStartConfiguration getConfiguration();

    default ServiceEnvironment getEnvironment(){
        return getConfiguration().getEnvironment();
    }

    default String getServiceName() {
        return getName() + "-" + getServiceName();
    }

    default String getName() {
        return getConfiguration().getName();
    }

    default UUID getUniqueId() { return getConfiguration().getUniqueId(); }

    default int getId(){
        return getConfiguration().getId();
    }

    default IRBucketHolder<ICloudGroup> getGroup(){
        return getConfiguration().getGroup();
    }

    String getMotd();
    void setMotd(String motd);

    ServiceState getServiceState();
    void setServiceState(ServiceState serviceState);

    int getMaxPlayers();
    void setMaxPlayers(int maxPlayers);

    default boolean isStatic(){
        return getConfiguration().isStatic();
    }

}
