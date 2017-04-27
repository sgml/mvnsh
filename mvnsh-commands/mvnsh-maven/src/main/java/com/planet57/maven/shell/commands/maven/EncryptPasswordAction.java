/*
 * Copyright (c) 2009-present the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 * The Eclipse Public License is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 *
 * The Apache License v2.0 is available at
 *   http://www.apache.org/licenses/LICENSE-2.0.html
 *
 * You may elect to redistribute this code under either of these licenses.
 */
package com.planet57.maven.shell.commands.maven;

import java.util.Properties;

import org.sonatype.plexus.components.cipher.DefaultPlexusCipher;
import org.sonatype.plexus.components.sec.dispatcher.DefaultSecDispatcher;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher;
import org.sonatype.plexus.components.sec.dispatcher.SecUtil;
import org.sonatype.plexus.components.sec.dispatcher.model.SettingsSecurity;

import com.planet57.gshell.command.Command;
import com.planet57.gshell.command.CommandContext;
import com.planet57.gshell.command.IO;
import com.planet57.gshell.command.CommandActionSupport;
import com.planet57.gshell.plexus.PlexusRuntime;
import com.planet57.gshell.util.NameValue;
import com.planet57.gshell.util.cli2.Argument;
import com.planet57.gshell.util.cli2.Option;
import com.planet57.gshell.util.pref.Preferences;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * Encrypt passwords.
 *
 * @author <a href="mailto:jason@planet57.com">Jason Dillon</a>
 * @since 0.9
 */
@Command(name = "encrypt-password")
@Preferences(path = "commands/encrypt-password")
public class EncryptPasswordAction
    extends CommandActionSupport
{
  private final PlexusRuntime plexus;

  @Nullable
  private Properties props;

  @Option(name = "D", longName = "define")
  protected void setProperty(final String input) {
    checkNotNull(input);

    if (props == null) {
      props = new Properties();
    }

    NameValue nv = NameValue.parse(input);
    props.setProperty(nv.name, nv.value);
  }

  @Option(name = "m", longName = "master")
  private boolean master;

  @Argument(required = true)
  private String password;

  @Inject
  public EncryptPasswordAction(final PlexusRuntime plexus) {
    this.plexus = checkNotNull(plexus);
  }

  @Override
  public Object execute(@Nonnull final CommandContext context) throws Exception {
    IO io = context.getIo();

    // HACK: Put all props into System, the security muck needs it
    if (props != null) {
      System.getProperties().putAll(props);
    }

    DefaultSecDispatcher dispatcher = (DefaultSecDispatcher) plexus.lookup(SecDispatcher.class, "maven");
    String result;

    if (master) {
      DefaultPlexusCipher cipher = new DefaultPlexusCipher();
      result = cipher.encryptAndDecorate(password, DefaultSecDispatcher.SYSTEM_PROPERTY_SEC_LOCATION);
    }
    else {
      String configurationFile = dispatcher.getConfigurationFile();

      // FIXME: sort out more general use of this, and user shell variables?
      if (configurationFile.startsWith("~")) {
        configurationFile = System.getProperty("user.home") + configurationFile.substring(1);
      }

      String file = System.getProperty(DefaultSecDispatcher.SYSTEM_PROPERTY_SEC_LOCATION, configurationFile);

      String master = null;

      SettingsSecurity sec = SecUtil.read(file, true);
      if (sec != null) {
        master = sec.getMaster();
      }
      checkState(master != null, "Master password is not set in the setting security file: %s", file);

      DefaultPlexusCipher cipher = new DefaultPlexusCipher();
      String masterPasswd = cipher.decryptDecorated(master, DefaultSecDispatcher.SYSTEM_PROPERTY_SEC_LOCATION);

      result = cipher.encryptAndDecorate(password, masterPasswd);
    }

    io.out.println(result);

    // maven core-its need 0 for success
    return Result.SUCCESS;
  }
}