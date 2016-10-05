/*
 * This file is part of Sponge, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://www.spongepowered.org>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spongepowered.common.text.action;

import net.minecraft.util.text.event.ClickEvent;

import org.spongepowered.api.text.action.ClickAction;
import org.spongepowered.common.interfaces.text.IMixinClickEvent;

import java.util.UUID;

public final class SpongeClickAction {

    private SpongeClickAction() {}

    private static ClickEvent.Action getType(ClickAction<?> action) {
        if (action instanceof ClickAction.OpenUrl) {
            return ClickEvent.Action.OPEN_URL;
        } else if (action instanceof ClickAction.RunCommand || action instanceof ClickAction.ExecuteCallback) {
            return ClickEvent.Action.RUN_COMMAND;
        } else if (action instanceof ClickAction.SuggestCommand) {
            return ClickEvent.Action.SUGGEST_COMMAND;
        } else if (action instanceof ClickAction.ChangePage) {
            return ClickEvent.Action.CHANGE_PAGE;
        }

        throw new UnsupportedOperationException(action.getClass().toString());
    }

    public static ClickEvent getHandle(ClickAction<?> action) {
        final String text;
        if (action instanceof ClickAction.ExecuteCallback) {
            UUID callbackId = SpongeCallbackHolder.getInstance().getOrCreateIdForCallback(((ClickAction.ExecuteCallback) action).getResult());
            text = SpongeCallbackHolder.CALLBACK_COMMAND_QUALIFIED + " " + callbackId;
        } else {
            text = action.getResult().toString();
        }

        ClickEvent event = new ClickEvent(getType(action), text);
        ((IMixinClickEvent) event).setHandle(action);
        return event;
    }

}
