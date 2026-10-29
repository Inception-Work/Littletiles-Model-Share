package work.inception.ltsmodelshare.command;

import static work.inception.ltsmodelshare.client.Translations.tr;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import work.inception.ltsmodelshare.client.InputHandler;

import java.util.Collections;
import java.util.List;

/**
 * /ltsmodels —— 打开模型面板（客户端命令，不发给服务端）。
 */
public class CommandLts extends CommandBase {

    @Override
    public String getName() {
        return "ltsmodels";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return tr("ltsmodelshare.command.usage");
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        InputHandler.openPanel();
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
                                          net.minecraft.util.math.BlockPos targetPos) {
        return Collections.emptyList();
    }

    /** 客户端命令不需要权限检查 */
    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return true;
    }
}
