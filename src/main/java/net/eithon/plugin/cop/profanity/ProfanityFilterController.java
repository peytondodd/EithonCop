package net.eithon.plugin.cop.profanity;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import net.eithon.library.extensions.EithonPlugin;
import net.eithon.library.file.FileMisc;
import net.eithon.library.plugin.Logger.DebugPrintLevel;
import net.eithon.library.time.TimeMisc;
import net.eithon.plugin.cop.Config;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;

public class ProfanityFilterController {
	static int profanityWordMinimumLength = 3;
	private EithonPlugin _eithonPlugin;
	private Blacklist _blacklist;
	private Whitelist _whitelist;

	public ProfanityFilterController(EithonPlugin eithonPlugin){
		this._eithonPlugin = eithonPlugin;
		this._blacklist = new Blacklist(eithonPlugin);
		this._blacklist.delayedLoad();
		this._whitelist = new Whitelist(eithonPlugin, this._blacklist);
		this._whitelist.delayedLoad(1);
		if (Config.V.saveSimilar) {
			this._blacklist.delayedLoadSimilar(2);
		}
		delayedLoadSeed(4);
	}

	public void disable() {
		this._whitelist.save();
		this._blacklist.save();
		this._blacklist.saveSimilar(this._whitelist);
	}

	private void delayedLoadSeed(double delaySeconds)
	{
		BukkitScheduler scheduler = Bukkit.getServer().getScheduler();
		scheduler.scheduleSyncDelayedTask(this._eithonPlugin, new Runnable() {
			public void run() {
				loadSeed();
			}
		}, TimeMisc.secondsToTicks(delaySeconds));		
	}

	void loadSeed() {
		File fileIn = getSeedInStorageFile();
		if (!fileIn.exists()) return;
		File fileOut = getSeedOutStorageFile();
		try (BufferedReader br = new BufferedReader(new FileReader(fileIn))) {
			String line;
			while ((line = br.readLine()) != null) {
				String filtered = profanityFilter(null, line);
				FileMisc.appendLine(fileOut, line);
				FileMisc.appendLine(fileOut, filtered);
				FileMisc.appendLine(fileOut, "");
			}
		} catch (FileNotFoundException e) {
			this._eithonPlugin.getEithonLogger().error("(1) Could not read from file %s: %s", fileIn.getName(), e.getMessage());
			e.printStackTrace();
		} catch (IOException e) {
			this._eithonPlugin.getEithonLogger().error("(2) Could not read from file %s: %s", fileIn.getName(), e.getMessage());
			e.printStackTrace();
		}
	}

	public String profanityFilter(Player player, String message) {
		ProfanityFilter filter = new ProfanityFilter(this, player, message);
		String filteredMessage = filter.getFilteredMessage();
		this._blacklist.delayedSaveOffenderMessage(player, message, filteredMessage);
		return filteredMessage;
	}

	private File getSeedInStorageFile() {
		File file = this._eithonPlugin.getDataFile("seedin.txt");
		return file;
	}

	private File getSeedOutStorageFile() {
		File file = this._eithonPlugin.getDataFile("seedout.txt");
		return file;
	}

	public String addProfanity(CommandSender sender, String word, boolean isLiteral, String synonyms) {
		if (word.length() < profanityWordMinimumLength) {
			Config.M.blackListWordMinimalLength.sendMessage(sender, profanityWordMinimumLength);
			return null;
		}
		Profanity profanity = this._blacklist.getProfanity(word);
		if (profanity == null) {
			profanity = this._blacklist.add(word, isLiteral);
			profanity.setSynonyms(synonyms.split("\\W"));
			this._blacklist.delayedSave();
			return profanity.getWord();
		}
		if (word.equalsIgnoreCase(profanity.getWord())) {
			profanity.setIsLiteral(isLiteral);
			profanity.setSynonyms(synonyms.split("\\W"));
			this._blacklist.delayedSave();
			return profanity.getWord();
		} else {
			Config.M.probablyDuplicateProfanity.sendMessage(sender, word, profanity.getWord());
		}
		return null;
	}

	public String removeProfanity(CommandSender sender, String word) {
		if (word.length() < profanityWordMinimumLength) {
			Config.M.blackListWordMinimalLength.sendMessage(sender, profanityWordMinimumLength);
			return null;
		}
		Profanity profanity = this._blacklist.getProfanity(word);
		if (profanity == null) {
			Config.M.profanityNotFound.sendMessage(sender, word);
			return null;
		}

		String found = profanity.getWord();
		if (!word.equalsIgnoreCase(found)) {
			Config.M.profanityNotFoundButSimilarFound.sendMessage(sender, word, found);
			return null;
		}
		this._blacklist.remove(word);
		this._blacklist.delayedSave();
		return found;
	}

	public String normalize(String word) {
		return Profanity.normalize(word);
	}

	public String addAccepted(CommandSender sender, String word) {
		if (word.length() < profanityWordMinimumLength) {
			Config.M.whitelistWordMinimalLength.sendMessage(sender, profanityWordMinimumLength);
			return null;
		}
		String normalized = Profanity.normalize(word);
		if (this._whitelist.isWhitelisted(normalized)) {
			Config.M.duplicateAcceptedWord.sendMessage(sender, word);
			return null;
		}
		Profanity profanity = this._blacklist.getProfanity(normalized);
		if (profanity != null) {
			if (normalized.equalsIgnoreCase(profanity.getWord())) {
				Config.M.acceptedWordWasBlacklisted.sendMessage(sender, word);
				return null;
			}
			this._whitelist.add(word);
			this._whitelist.delayedSave();
			return profanity.getWord();
		}
		Config.M.acceptedWordWasNotBlacklisted.sendMessage(sender, word);
		return null;
	}

	public String removeAccepted(CommandSender sender, String word) {
		if (word.length() < profanityWordMinimumLength) {
			Config.M.whitelistWordMinimalLength.sendMessage(sender, profanityWordMinimumLength);
			return null;
		}
		String normalized = Profanity.normalize(word);
		if (!this._whitelist.isWhitelisted(normalized)) {
			Config.M.acceptedWordNotFound.sendMessage(sender, word);
			return null;
		}
		
		this._whitelist.remove(normalized);
		this._whitelist.delayedSave();
		return normalized;
	}

	boolean isWhitelisted(String word) {
		return this._whitelist.isWhitelisted(word);
	}

	String replaceIfBlacklisted(Player player, String normalized, String originalWord) {
		return this._blacklist.replaceIfBlacklisted(player, normalized, originalWord);
	}

	void verbose(String method, String format, Object... args) {
		String message = String.format(format, args);
		this._eithonPlugin.getEithonLogger().debug(DebugPrintLevel.VERBOSE, "%s: %s", method, message);
	}
}