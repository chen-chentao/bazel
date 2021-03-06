// Copyright 2017 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.devtools.build.lib.runtime;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Strings;
import com.google.devtools.build.lib.server.FailureDetails.FailureDetail;
import com.google.devtools.build.lib.server.FailureDetails.Filesystem;
import com.google.devtools.build.lib.server.FailureDetails.Filesystem.Code;
import com.google.devtools.build.lib.unix.UnixFileSystem;
import com.google.devtools.build.lib.util.AbruptExitException;
import com.google.devtools.build.lib.util.DetailedExitCode;
import com.google.devtools.build.lib.util.ExitCode;
import com.google.devtools.build.lib.util.OS;
import com.google.devtools.build.lib.vfs.DigestHashFunction;
import com.google.devtools.build.lib.vfs.DigestHashFunction.DigestFunctionConverter;
import com.google.devtools.build.lib.vfs.JavaIoFileSystem;
import com.google.devtools.build.lib.vfs.PathFragment;
import com.google.devtools.build.lib.windows.WindowsFileSystem;
import com.google.devtools.common.options.OptionsParsingException;
import com.google.devtools.common.options.OptionsParsingResult;

/**
 * Module to provide a {@link com.google.devtools.build.lib.vfs.FileSystem} instance that uses
 * {@code SHA256} as the default hash function, or else what's specified by {@code
 * -Dbazel.DigestFunction}.
 *
 * <p>Because of Blaze/Bazel divergence we can't make the {@link
 * com.google.devtools.build.lib.vfs.FileSystem} class use {@code SHA256} by default.
 */
public class BazelFileSystemModule extends BlazeModule {
  @Override
  public ModuleFileSystem getFileSystem(
      OptionsParsingResult startupOptions, PathFragment realExecRootBase)
      throws AbruptExitException {
    BlazeServerStartupOptions options =
        checkNotNull(startupOptions.getOptions(BlazeServerStartupOptions.class));
    DigestHashFunction digestHashFunction = options.digestHashFunction;
    if (digestHashFunction == null) {
      String value = System.getProperty("bazel.DigestFunction", "SHA256");
      try {
        digestHashFunction = new DigestFunctionConverter().convert(value);
      } catch (OptionsParsingException e) {
        throw new AbruptExitException(
            DetailedExitCode.of(
                ExitCode.COMMAND_LINE_ERROR,
                FailureDetail.newBuilder()
                    .setMessage(Strings.nullToEmpty(e.getMessage()))
                    .setFilesystem(
                        Filesystem.newBuilder()
                            .setCode(Code.DEFAULT_DIGEST_HASH_FUNCTION_INVALID_VALUE))
                    .build()),
            e);
      }
    }
    boolean enableSymLinks = options.enableWindowsSymlinks;
    if ("0".equals(System.getProperty("io.bazel.EnableJni"))) {
      // Ignore UnixFileSystem, to be used for bootstrapping.
      return ModuleFileSystem.create(
          OS.getCurrent() == OS.WINDOWS
              ? new WindowsFileSystem(digestHashFunction, enableSymLinks)
              : new JavaIoFileSystem(digestHashFunction));
    }
    // The JNI-based UnixFileSystem is faster, but on Windows it is not available.
    return ModuleFileSystem.create(
        OS.getCurrent() == OS.WINDOWS
            ? new WindowsFileSystem(digestHashFunction, enableSymLinks)
            : new UnixFileSystem(digestHashFunction, options.unixDigestHashAttributeName));
  }
}
