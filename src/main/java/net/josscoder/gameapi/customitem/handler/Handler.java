package net.josscoder.gameapi.customitem.handler;

import cn.nukkit.Player;
import net.josscoder.gameapi.user.User;

public interface Handler {
  void executeAction(User user, Player player);
}
