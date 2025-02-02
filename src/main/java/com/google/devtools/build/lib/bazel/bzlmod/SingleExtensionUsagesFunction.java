// Copyright 2021 The Bazel Authors. All rights reserved.
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
//

package com.google.devtools.build.lib.bazel.bzlmod;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableTable;
import com.google.devtools.build.skyframe.SkyFunction;
import com.google.devtools.build.skyframe.SkyFunctionException;
import com.google.devtools.build.skyframe.SkyKey;
import com.google.devtools.build.skyframe.SkyValue;

/**
 * A simple SkyFunction that takes the information needed by a {@link SingleExtensionEvalFunction}
 * out of {@link BazelModuleResolutionValue} and stores it in a SkyValue.
 *
 * <p>The whole raison d'être of this function is to avoid unnecessary reruns of module extensions.
 * Whenever any information in the whole dependency graph changes, {@link
 * BazelModuleResolutionFunction} is rerun, producing a new {@link BazelModuleResolutionValue}. If
 * {@link SingleExtensionEvalFunction} were to directly depend on {@link
 * BazelModuleResolutionValue}, any such change would cause ALL module extensions to be rerun.
 * Instead, by storing the input needed by a single {@link SingleExtensionEvalFunction}, we can rely
 * on Skyframe's change pruning feature to make sure that we only rerun the module extension whose
 * input data actually changed.
 */
public class SingleExtensionUsagesFunction implements SkyFunction {

  @Override
  public SkyValue compute(SkyKey skyKey, Environment env)
      throws SkyFunctionException, InterruptedException {
    BazelModuleResolutionValue bazelModuleResolutionValue =
        (BazelModuleResolutionValue) env.getValue(BazelModuleResolutionValue.KEY);
    if (bazelModuleResolutionValue == null) {
      return null;
    }

    ModuleExtensionId id = (ModuleExtensionId) skyKey.argument();
    ImmutableTable<ModuleExtensionId, ModuleKey, ModuleExtensionUsage> usagesTable =
        bazelModuleResolutionValue.getExtensionUsagesTable();
    return SingleExtensionUsagesValue.create(
        usagesTable.row(id),
        bazelModuleResolutionValue.getExtensionUniqueNames().get(id),
        // Filter abridged modules down to only those that actually used this extension.
        bazelModuleResolutionValue.getAbridgedModules().stream()
            .filter(module -> usagesTable.contains(id, module.getKey()))
            .collect(toImmutableList()));
  }

  @Override
  public String extractTag(SkyKey skyKey) {
    return null;
  }
}
