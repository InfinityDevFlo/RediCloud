package net.suqatri.cloud.api.minecraft;

import net.suqatri.cloud.api.service.ServiceState;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.TimeUnit;

public class MinecraftCloudPlugin extends JavaPlugin {

    private MinecraftCloudAPI cloudAPI;

    @Override
    public void onLoad() {
        cloudAPI = new MinecraftCloudAPI(this);
    }

    @Override
    public void onEnable() {
        cloudAPI.getService().setServiceState(ServiceState.RUNNING_UNDEFINED);
        cloudAPI.getService().update();
    }

    @Override
    public void onDisable() {
        cloudAPI.shutdown(false);
    }
}
