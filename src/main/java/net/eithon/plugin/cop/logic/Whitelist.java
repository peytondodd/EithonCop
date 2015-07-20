package net.eithon.plugin.cop.logic;

import java.io.File;
import java.util.HashMap;

import net.eithon.library.extensions.EithonPlugin;
import net.eithon.library.json.FileContent;
import net.eithon.library.plugin.Logger.DebugPrintLevel;
import net.eithon.library.time.TimeMisc;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitScheduler;
import org.json.simple.JSONArray;

class Whitelist {
	private EithonPlugin _eithonPlugin;
	private Blacklist _blacklist;
	private HashMap<String, Profanity> _hashMap;

	public Whitelist(EithonPlugin eithonPlugin, Blacklist blacklist)
	{
		this._eithonPlugin = eithonPlugin;
		this._blacklist = blacklist;
		this._hashMap = new HashMap<String, Profanity>();
	}
	
	public Profanity add(String word) {
		String normalized = Profanity.normalize(word);
		if (isWhitelisted(normalized)) return null;
		Profanity profanity = this._blacklist.getProfanity(normalized);
		this._hashMap.put(normalized, profanity);
		return profanity;
	}

	public boolean isWhitelisted(String word) { return getProfanity(word) != null; }

	public Profanity getProfanity(String word) {
		return this._hashMap.get(Profanity.normalize(word));
	}

	public void delayedSave()
	{
		BukkitScheduler scheduler = Bukkit.getServer().getScheduler();
		scheduler.scheduleSyncDelayedTask(this._eithonPlugin, new Runnable() {
			public void run() {
				save();
			}
		});		
	}

	@SuppressWarnings("unchecked")
	public
	void save() {
		JSONArray whitelist = new JSONArray();
		for (String word : this._hashMap.keySet()) {
			whitelist.add(word);
		}
		if ((whitelist == null) || (whitelist.size() == 0)) {
			this._eithonPlugin.getEithonLogger().info("No words saved in whitelist.");
			return;
		}
		this._eithonPlugin.getEithonLogger().info("Saving %d words in whitelist", whitelist.size());
		File file = getWhitelistStorageFile();

		FileContent fileContent = new FileContent("Whitelist", 1, whitelist);
		fileContent.save(file);
	}

	public void delayedLoad(double delaySeconds)
	{
		BukkitScheduler scheduler = Bukkit.getServer().getScheduler();
		scheduler.scheduleSyncDelayedTask(this._eithonPlugin, new Runnable() {
			public void run() {
				load();
			}
		}, TimeMisc.secondsToTicks(delaySeconds));		
	}

	void load() {
		File file = getWhitelistStorageFile();
		FileContent fileContent = FileContent.loadFromFile(file);
		if (fileContent == null) {
			this._eithonPlugin.getEithonLogger().debug(DebugPrintLevel.MAJOR, "File was empty.");
			return;			
		}
		JSONArray array = (JSONArray) fileContent.getPayload();
		if ((array == null) || (array.size() == 0)) {
			this._eithonPlugin.getEithonLogger().debug(DebugPrintLevel.MAJOR, "The whitelist was empty.");
			return;
		}
		this._eithonPlugin.getEithonLogger().info("Restoring %d words from whitelist file.", array.size());
		this._hashMap = new HashMap<String, Profanity>();
		for (int i = 0; i < array.size(); i++) {
			String word = null;
			try {
				word = (String) array.get(i);
				add(word);
			} catch (Exception e) {
				this._eithonPlugin.getEithonLogger().error("Could not load word %d (exception).", i);
				if (word != null) this._eithonPlugin.getEithonLogger().error("Could not load word %s", word);
				this._eithonPlugin.getEithonLogger().error("%s", e.toString());
				throw e;
			}
		}
	}

	private File getWhitelistStorageFile() {
		File file = this._eithonPlugin.getDataFile("whitelist.json");
		return file;
	}
}
