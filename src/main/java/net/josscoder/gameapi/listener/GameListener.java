/*
 * Copyright 2021-2055 Josscoder
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package net.josscoder.gameapi.listener;

import cn.nukkit.event.Listener;
import net.josscoder.gameapi.Game;
import net.josscoder.gameapi.user.factory.UserFactory;

public abstract class GameListener<T extends Game> implements Listener {

  protected final T game;
  protected final UserFactory userFactory;

  public GameListener(T game) {
    this.game = game;
    this.userFactory = game.getUserFactory();
  }
}
