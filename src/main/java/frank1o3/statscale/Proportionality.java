package frank1o3.statscale;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.resources.Identifier;
import net.minecraft.server.permissions.Permissions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.brigadier.arguments.FloatArgumentType;

public class Proportionality implements ModInitializer {
	public static final String MOD_ID = "proportionality";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environmet) -> {
			dispatcher.register(Commands.literal("scale")
					.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
					.then(Commands.literal("get")
							.executes(context -> {
								return 1;
							}))
					.then(Commands.literal("set")
							.then(Commands.argument("value", FloatArgumentType.floatArg(0.1f, 32.0f))
									.executes(context -> HandleCallbacks.MeSet(context)))));

		});
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
