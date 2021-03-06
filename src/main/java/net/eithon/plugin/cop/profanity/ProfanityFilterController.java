package net.eithon.plugin.cop.profanity;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import net.eithon.library.exceptions.FatalException;
import net.eithon.library.exceptions.TryAgainException;
import net.eithon.library.extensions.EithonPlugin;
import net.eithon.library.file.FileMisc;
import net.eithon.library.mysql.Database;
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

	public ProfanityFilterController(EithonPlugin eithonPlugin, Database database) throws FatalException{
		this._eithonPlugin = eithonPlugin;
		Profanity.initialize(database);
		Blacklist.initialize(database);
		Whitelist.initialize(database);
		this._blacklist = new Blacklist(eithonPlugin);
		this._blacklist.delayedLoad();
		this._whitelist = new Whitelist(eithonPlugin, this._blacklist);
		this._whitelist.delayedLoad(1);
		delayedLoadSeed(4);
	}

	public void disable() {
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
			this._eithonPlugin.logError("(1) Could not read from file %s: %s", fileIn.getName(), e.getMessage());
			e.printStackTrace();
		} catch (IOException e) {
			this._eithonPlugin.logError("(2) Could not read from file %s: %s", fileIn.getName(), e.getMessage());
			e.printStackTrace();
		}
	}

	public String profanityFilter(Player player, String message) {
		if (message == null) return null;
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

	public String addProfanity(CommandSender sender, String word, boolean isLiteral) throws FatalException, TryAgainException {
		if (word.length() < profanityWordMinimumLength) {
			Config.M.blackListWordMinimalLength.sendMessage(sender, profanityWordMinimumLength);
			return null;
		}
		Profanity profanity = this._blacklist.getProfanity(word);
		if ((profanity != null) && word.equalsIgnoreCase(profanity.getWord())) {
			profanity.setIsLiteral(isLiteral);
			profanity.save();
			return profanity.getWord();
		}
		if (profanity != null) {
			Config.M.probablyDuplicateProfanity.sendMessage(sender, word, profanity.getWord());
		}
		profanity = this._blacklist.add(word, isLiteral);
		return profanity.getWord();
	}

	public String removeProfanity(CommandSender sender, String word) throws FatalException, TryAgainException {
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
		return found;
	}

	public String normalize(String word) {
		return Profanity.normalize(word);
	}

	public String addAccepted(CommandSender sender, String word) throws FatalException, TryAgainException {
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
			this._whitelist.create(word);
			return profanity.getWord();
		}
		Config.M.acceptedWordWasNotBlacklisted.sendMessage(sender, word);
		return null;
	}

	public String removeAccepted(CommandSender sender, String word) throws FatalException, TryAgainException {
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
		return normalized;
	}

	boolean isWhitelisted(String word) {
		return this._whitelist.isWhitelisted(word);
	}

	String replaceIfBlacklisted(Player player, String normalized, String originalWord) {
		return this._blacklist.replaceIfBlacklisted(player, normalized, originalWord);
	}

	public List<String> getAllBlacklistedWords() {
		return Arrays.asList(this._blacklist.getAllWords());
	}

	public List<String> getAllWhitelistedWords() {
		return Arrays.asList(this._whitelist.getAllWords());
	}
	
	void verboseLog(String className, String method, String format, Object... args)
	{
		this._eithonPlugin.dbgVerbose(className, method, format, args);
	}
}