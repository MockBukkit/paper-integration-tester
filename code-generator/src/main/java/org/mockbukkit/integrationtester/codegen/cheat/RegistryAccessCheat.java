package org.mockbukkit.integrationtester.codegen.cheat;

import io.papermc.paper.datacomponent.DataComponentType;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Biome;
import org.bukkit.block.BlockType;
import org.bukkit.block.banner.PatternType;
import org.bukkit.damage.DamageType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.entity.memory.MemoryKey;
import org.bukkit.generator.structure.Structure;
import org.bukkit.inventory.ItemType;
import org.bukkit.inventory.MenuType;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.map.MapCursor;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.jspecify.annotations.Nullable;

public class RegistryAccessCheat implements io.papermc.paper.registry.RegistryAccess {
    @Override
    public @Nullable <T extends Keyed> Registry<T> getRegistry(Class<T> aClass) {
        return new RegistryCheat<>(aClass);
    }

    @Override
    public <T extends Keyed> Registry<T> getRegistry(RegistryKey<T> registryKey) {
        if (RegistryKey.ATTRIBUTE == registryKey) {
            return (RegistryCheat<T>) new RegistryCheat<>(Attribute.class);
        }
        if (RegistryKey.BANNER_PATTERN == registryKey) {
            return (RegistryCheat<T>) new RegistryCheat<>(PatternType.class);
        }
        if (RegistryKey.BLOCK == registryKey) {
            return (RegistryCheat<T>) new RegistryCheat<>(BlockType.class);
        }
        if (RegistryKey.GAME_EVENT == registryKey) {
            return (RegistryCheat<T>) new RegistryCheat<>(GameEvent.class);
        }
        if (RegistryKey.STRUCTURE_TYPE == registryKey) {
            return (RegistryCheat<T>) new RegistryCheat<>(StructureType.class);
        }
        if (RegistryKey.MOB_EFFECT == registryKey) {
            return (RegistryCheat<T>) new RegistryCheat<>(PotionEffectType.class);
        }
        if (RegistryKey.ITEM == registryKey) {
            return (RegistryCheat<T>) new RegistryCheat<>(ItemType.class);
        }
        if (RegistryKey.CAT_VARIANT == registryKey) {
            return (RegistryCheat<T>) new RegistryCheat<>(Cat.Type.class);
        }
        if (RegistryKey.FROG_VARIANT == registryKey) {
            return (RegistryCheat<T>) new RegistryCheat<>(Frog.Variant.class);
        }
        if (RegistryKey.VILLAGER_PROFESSION == registryKey) {
            return (RegistryCheat<T>) new RegistryCheat<>(Villager.Profession.class);
        }
        if (RegistryKey.VILLAGER_TYPE == registryKey) {
            return (RegistryCheat<T>) new RegistryCheat<>(Villager.Type.class);
        }
        if (RegistryKey.MAP_DECORATION_TYPE == registryKey) {
            return (RegistryCheat<T>) new RegistryCheat<>(MapCursor.Type.class);
        }
        if (RegistryKey.MENU == registryKey) {
            return (RegistryCheat<T>) new RegistryCheat<>(MenuType.class);
        }
        if (RegistryKey.ATTRIBUTE == registryKey) {
            return (RegistryCheat<T>) new RegistryCheat<>(Attribute.class);
        }
        if (RegistryKey.FLUID == registryKey) {
            return (RegistryCheat<T>) new RegistryCheat<>(Fluid.class);
        }
        if (RegistryKey.SOUND_EVENT == registryKey) {
            return (RegistryCheat<T>) new RegistryCheat<>(Sound.class);
        }
        if (RegistryKey.DATA_COMPONENT_TYPE == registryKey) {
            return (RegistryCheat<T>) new RegistryCheat<>(DataComponentType.class);
        }
        if (RegistryKey.BIOME == registryKey) {
            return (RegistryCheat<T>) new RegistryCheat<>(Biome.class);
        }
        if (RegistryKey.STRUCTURE == registryKey) {
            return (RegistryCheat<T>) new RegistryCheat<>(Structure.class);
        }
        if (RegistryKey.TRIM_MATERIAL == registryKey) {
            return (RegistryCheat<T>) new RegistryCheat<>(TrimMaterial.class);
        }
        if (RegistryKey.TRIM_PATTERN == registryKey) {
            return (RegistryCheat<T>) new RegistryCheat<>(TrimPattern.class);
        }
        if (RegistryKey.DAMAGE_TYPE == registryKey) {
            return (RegistryCheat<T>) new RegistryCheat<>(DamageType.class);
        }
        if (RegistryKey.WOLF_VARIANT == registryKey) {
            return (RegistryCheat<T>) new RegistryCheat<>(Wolf.Variant.class);
        }
        if (RegistryKey.ENCHANTMENT == registryKey) {
            return (RegistryCheat<T>) new RegistryCheat<>(Enchantment.class);
        }
        if (RegistryKey.JUKEBOX_SONG == registryKey) {
            return (RegistryCheat<T>) new RegistryCheat<>(JukeboxSong.class);
        }
        if (RegistryKey.BANNER_PATTERN == registryKey) {
            return (RegistryCheat<T>) new RegistryCheat<>(PatternType.class);
        }
        if (RegistryKey.PAINTING_VARIANT == registryKey) {
            return (RegistryCheat<T>) new RegistryCheat<>(Art.class);
        }
        if (RegistryKey.INSTRUMENT == registryKey) {
            return (RegistryCheat<T>) new RegistryCheat<>(MusicInstrument.class);
        }
        if (RegistryKey.ENTITY_TYPE == registryKey) {
            return (RegistryCheat<T>) new RegistryCheat<>(EntityType.class);
        }
        if (RegistryKey.PARTICLE_TYPE == registryKey) {
            return (RegistryCheat<T>) new RegistryCheat<>(Particle.class);
        }
        if (RegistryKey.POTION == registryKey) {
            return (RegistryCheat<T>) new RegistryCheat<>(PotionType.class);
        }
        if (RegistryKey.MEMORY_MODULE_TYPE == registryKey) {
            return (RegistryCheat<T>) new RegistryCheat<>(MemoryKey.class);
        }
        return null;
    }
}
