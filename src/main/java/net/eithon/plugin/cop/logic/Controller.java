package net.eithon.plugin.cop.logic;

import net.eithon.library.extensions.EithonPlugin;

import org.bukkit.entity.Player;

public class Controller {

	private Blacklist _blacklist;

	public Controller(EithonPlugin eithonPlugin){
		this._blacklist = new Blacklist();
	}

	public String profanityFilter(Player player, String message) {
		char[] inCharArray = message.toCharArray();
		String transformedInMessage = Leet.decode(message.toLowerCase());
		transformedInMessage = transformedInMessage.replaceAll("\\s-", " ");
		char[] transformedCharArray = transformedInMessage.toCharArray();
		StringBuilder inWord = new StringBuilder("");
		StringBuilder transformedWord = new StringBuilder("");
		String outWord;
		StringBuilder outMessage = new StringBuilder("");
		for (int pos = 0; pos < transformedCharArray.length; pos++) {
			char inChar = inCharArray[pos];
			char transformedChar = transformedCharArray[pos];
			if (transformedChar != ' ') {
				inWord.append(inChar);
				transformedWord.append(transformedChar);
				continue;
			}
			// We have a word
			if (transformedWord.length() > 0) {
				outWord = replace(transformedWord.toString(), inWord.toString());
				outMessage.append(outWord);
				outMessage.append(inChar);
				inWord = new StringBuilder();
				transformedWord = new StringBuilder("");
			}
		}
		// We have a word
		if (transformedWord.length() > 0) {
			outWord = replace(transformedWord.toString(), inWord.toString());
			outMessage.append(outWord);
		}

		return outMessage.toString();
	}

	private String replace(String transformedWord, String inWord) {
		String outWord = this._blacklist.replaceIfBlacklisted(transformedWord);
		if (outWord == null) return inWord;
		return casifyAsReferenceWord(outWord, inWord);
	}

	private String casifyAsReferenceWord(String outWord, String referenceWord) {
		char[] charArray = referenceWord.toCharArray();
		char c = charArray[0];
		boolean firstCharacterIsUpperCase = Character.isAlphabetic(c) && Character.isUpperCase(c);
		if (!firstCharacterIsUpperCase) return outWord.toLowerCase();
		for (int i = 1; i < charArray.length; i++) {
			c = charArray[i];
			if (Character.isAlphabetic(c)) {
				if (Character.isUpperCase(c)) return outWord.toUpperCase();
				else {
					StringBuilder result = new StringBuilder();
					result.append(outWord.substring(0, 0).toUpperCase());
					result.append(outWord.substring(1).toLowerCase());
					return result.toString();
				}
			}
		}
		return outWord.toLowerCase();
	}
}