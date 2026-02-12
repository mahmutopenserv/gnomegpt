package com.gnomegpt;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class GnomeGptPluginTest
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(GnomeGptPlugin.class);
        RuneLite.main(args);
    }
}
