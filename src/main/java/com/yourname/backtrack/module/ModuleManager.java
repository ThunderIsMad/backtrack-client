package com.yourname.backtrack.module;

import com.yourname.backtrack.module.impl.AutoClickerModule;
import com.yourname.backtrack.module.impl.AutoRespawnModule;
import com.yourname.backtrack.module.impl.AutoSprintModule;
import com.yourname.backtrack.module.impl.FullBrightModule;
import com.yourname.backtrack.module.impl.JumpResetModule;
import com.yourname.backtrack.module.impl.KeepSprintModule;
import com.yourname.backtrack.module.impl.WTapModule;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ModuleManager {

    private final List<Module> modules = new ArrayList<>();

    public ModuleManager() {
        registerModule(new AutoSprintModule());
        registerModule(new FullBrightModule());
        registerModule(new AutoRespawnModule());
        registerModule(new KeepSprintModule());
        registerModule(new WTapModule());
        registerModule(new JumpResetModule());
        registerModule(new AutoClickerModule());
    }

    private void registerModule(Module module) {
        module.getHudSettings().setDefaultPosition(5, 5 + modules.size() * 14);
        modules.add(module);
        ClientRegistry.registerKeyBinding(module.getKeyBinding());
        MinecraftForge.EVENT_BUS.register(module);
    }

    public List<Module> getModules() {
        return Collections.unmodifiableList(modules);
    }
}
