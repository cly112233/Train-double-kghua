package com.kghua.npcai.mail;

import net.minecraft.world.entity.player.Player;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.entity.EntityComponentFactoryRegistry;
import org.ladysnake.cca.api.v3.entity.EntityComponentInitializer;
import org.ladysnake.cca.api.v3.entity.RespawnCopyStrategy;

/**
 * 邮箱 CCA 组件注册（cardinal-components entrypoint）。
 * 从旧版基座 cca.SREComponents.beginRegistration(Player.class, MailboxComponent.KEY) 移植。
 */
public class MailComponents implements EntityComponentInitializer {
    public static final ComponentKey<MailboxComponent> KEY = MailboxComponent.KEY;

    @Override
    public void registerEntityComponentFactories(EntityComponentFactoryRegistry registry) {
        registry.beginRegistration(Player.class, KEY)
                .respawnStrategy(RespawnCopyStrategy.ALWAYS_COPY)
                .end(MailboxComponent::new);
    }
}
