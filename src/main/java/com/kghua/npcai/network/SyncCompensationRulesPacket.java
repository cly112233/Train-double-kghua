package com.kghua.npcai.network;

import com.kghua.npcai.data.CompensationRule;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record SyncCompensationRulesPacket(List<CompensationRule> rules) implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("western_cowboy", "sync_compensation_rules");
    public static final Type<SyncCompensationRulesPacket> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncCompensationRulesPacket> CODEC =
        StreamCodec.of(
            (buf, pkt) -> {
                buf.writeVarInt(pkt.rules.size());
                for (CompensationRule rule : pkt.rules) {
                    buf.writeUUID(rule.getId());
                    buf.writeUtf(rule.getTitle());
                    buf.writeUtf(rule.getDeathReason());
                    buf.writeVarInt(rule.getRequiredDeaths());
                    buf.writeVarInt(rule.getCommands().size());
                    for (CompensationRule.CommandEntry e : rule.getCommands()) {
                        buf.writeUtf(e.name);
                        buf.writeUtf(e.command);
                    }
                }
            },
            buf -> {
                int count = buf.readVarInt();
                List<CompensationRule> list = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    CompensationRule rule = new CompensationRule(buf.readUUID());
                    rule.setTitle(buf.readUtf());
                    rule.setDeathReason(buf.readUtf());
                    rule.setRequiredDeaths(buf.readVarInt());
                    int cmdCount = buf.readVarInt();
                    for (int j = 0; j < cmdCount; j++) {
                        CompensationRule.CommandEntry e = new CompensationRule.CommandEntry();
                        e.name = buf.readUtf();
                        e.command = buf.readUtf();
                        rule.getCommands().add(e);
                    }
                    list.add(rule);
                }
                return new SyncCompensationRulesPacket(list);
            }
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
