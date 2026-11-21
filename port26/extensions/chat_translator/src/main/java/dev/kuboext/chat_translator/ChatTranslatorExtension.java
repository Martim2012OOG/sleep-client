package dev.kuboext.chat_translator;

import dev.sleepclient.mods.ChatTranslatorMod;
import dev.sleepclient.ext.KuboApi;
import dev.sleepclient.ext.KuboExtension;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/** Sleep extension "Chat Translator": Translates chat from other languages, and lets you write in any language with !en. */
public class ChatTranslatorExtension implements KuboExtension {

	@Override
	public void register(KuboApi api) {
		api.addMod(new ChatTranslatorMod());
	}
}
