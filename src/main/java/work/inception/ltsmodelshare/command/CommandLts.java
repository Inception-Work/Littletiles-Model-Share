package work.inception.ltsmodelshare.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import work.inception.ltsmodelshare.client.InputHandler;

/** Client command: /ltsmodels opens the model browser. */
public final class CommandLts {
    private CommandLts() { }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ltsmodels").executes(context -> {
            InputHandler.openPanel();
            return 1;
        }));
    }
}
