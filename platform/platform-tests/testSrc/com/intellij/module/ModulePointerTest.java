// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.module;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.*;
import com.intellij.openapi.module.impl.ModulePointerManagerImpl;
import com.intellij.testFramework.PlatformTestCase;
import org.assertj.core.util.Maps;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author nik
 */
public class ModulePointerTest extends PlatformTestCase {
  public void testCreateByName() {
    final ModulePointer pointer = getPointerManager().create("m");
    assertSame(pointer, getPointerManager().create("m"));
    assertNull(pointer.getModule());
    assertEquals("m", pointer.getModuleName());

    final Module module = addModule("m");

    assertSame(module, pointer.getModule());
    assertEquals("m", pointer.getModuleName());
  }

  public void testCreateByModule() {
    final Module module = addModule("x");
    final ModulePointer pointer = getPointerManager().create(module);
    assertSame(pointer, getPointerManager().create(module));
    assertSame(pointer, getPointerManager().create("x"));
    assertSame(module, pointer.getModule());
    assertEquals("x", pointer.getModuleName());

    deleteModule(module);

    assertNull(pointer.getModule());
    assertEquals("x", pointer.getModuleName());

    final Module newModule = addModule("x");
    assertSame(pointer, getPointerManager().create(newModule));
  }

  private void deleteModule(Module module) {
    ModifiableModuleModel model = getModuleManager().getModifiableModel();
    model.disposeModule(module);
    commitModel(model);
  }

  public void testRenameModule() throws Exception {
    final ModulePointer pointer = getPointerManager().create("abc");
    final Module module = addModule("abc");
    renameModule(module, "xyz");
    assertSame(module, pointer.getModule());
    assertEquals("xyz", pointer.getModuleName());
  }

  private void renameModule(Module module, String newName) throws ModuleWithNameAlreadyExists {
    ModifiableModuleModel model = getModuleManager().getModifiableModel();
    model.renameModule(module, newName);
    commitModel(model);
  }

  public void testMergePointersAfterRenamingModule() throws ModuleWithNameAlreadyExists {
    ModulePointer pointer = getPointerManager().create("oldName");
    Module module = addModule("oldName");
    ModulePointer newPointer = getPointerManager().create("newName");
    renameModule(module, "newName");

    assertSame(module, pointer.getModule());
    assertEquals("newName", pointer.getModuleName());
    assertSame(module, newPointer.getModule());
    assertEquals("newName", newPointer.getModuleName());

    deleteModule(module);
    assertNull(pointer.getModule());
    assertNull(newPointer.getModule());
  }

  public void testDisposePointerFromUncommittedModifiableModel() {
    ModulePointerManager pointerManager = getPointerManager();
    final ModulePointer pointer = pointerManager.create("xxx");

    final ModifiableModuleModel modifiableModel = getModuleManager().getModifiableModel();
    final Module module = modifiableModel.newModule(myProject.getBasePath() + "/xxx.iml", EmptyModuleType.getInstance().getId());
    assertThat(pointerManager.create(module)).isSameAs(pointer);
    assertThat(pointerManager.create("xxx")).isSameAs(pointer);

    assertThat(pointer.getModule()).isSameAs(module);
    assertThat(pointer.getModuleName()).isEqualTo("xxx");

    ApplicationManager.getApplication().runWriteAction(() -> modifiableModel.dispose());

    assertThat(pointer.getModule()).isNull();
    assertThat(pointer.getModuleName()).isEqualTo("xxx");
  }

  public void testCreatePointerForRenamedModule() {
    ((ModulePointerManagerImpl)getPointerManager()).setRenamingScheme(Maps.newHashMap("oldName", "newName"));
    Module module = addModule("newName");
    ModulePointer pointer = getPointerManager().create("oldName");
    assertEquals("newName", pointer.getModuleName());
    assertSame(module, pointer.getModule());
  }

  public void testUpdateRenamingSchemeOnModuleRename() throws ModuleWithNameAlreadyExists {
    ((ModulePointerManagerImpl)getPointerManager()).setRenamingScheme(Maps.newHashMap("oldName", "newName"));
    Module module = addModule("newName");
    renameModule(module, "updatedNewName");
    ModulePointer pointer = getPointerManager().create("oldName");
    assertEquals("updatedNewName", pointer.getModuleName());
    assertSame(module, pointer.getModule());
  }

  public void testUpdateUnresolvedPointerWhenRenamingSchemeIsApplied() {
    Module module = addModule("oldName");
    ModulePointer pointer = getPointerManager().create(module);
    assertEquals("oldName", pointer.getModuleName());

    deleteModule(module);
    Module newModule = addModule("newName");

    ((ModulePointerManagerImpl)getPointerManager()).setRenamingScheme(Maps.newHashMap("oldName", "newName"));
    assertEquals("newName", pointer.getModuleName());
    assertSame(newModule, pointer.getModule());
  }

  public void testUpdateValidPointerWhenRenamingSchemeIsApplied() {
    Module module = addModule("oldName");
    ModulePointer pointer = getPointerManager().create(module);
    assertEquals("oldName", pointer.getModuleName());

    ((ModulePointerManagerImpl)getPointerManager()).setRenamingScheme(Maps.newHashMap("oldName", "newName"));

    deleteModule(module);
    Module newModule = addModule("newName");

    assertEquals("newName", pointer.getModuleName());
    assertSame(newModule, pointer.getModule());
  }

  public void testUpdateValidAndUnresolvedPointerWhenRenamingSchemeIsApplied() {
    Module module = addModule("oldName");
    ModulePointer pointer = getPointerManager().create(module);
    assertEquals("oldName", pointer.getModuleName());
    ModulePointer newPointer = getPointerManager().create("newName");

    ((ModulePointerManagerImpl)getPointerManager()).setRenamingScheme(Maps.newHashMap("oldName", "newName"));

    deleteModule(module);
    Module newModule = addModule("newName");

    assertEquals("newName", pointer.getModuleName());
    assertSame(newModule, pointer.getModule());
    assertEquals("newName", newPointer.getModuleName());
    assertSame(newModule, newPointer.getModule());
  }

  private ModuleManager getModuleManager() {
    return ModuleManager.getInstance(myProject);
  }

  private Module addModule(final String name) {
    final ModifiableModuleModel model = getModuleManager().getModifiableModel();
    final Module module = model.newModule(myProject.getBasePath() + "/" + name + ".iml", EmptyModuleType.getInstance().getId());
    commitModel(model);
    disposeOnTearDown(new Disposable() {
      @Override
      public void dispose() {
        if (!module.isDisposed()) {
          getModuleManager().disposeModule(module);
        }
      }
    });
    return module;
  }

  private static void commitModel(final ModifiableModuleModel model) {
    WriteAction.runAndWait(() -> model.commit());
  }

  private ModulePointerManager getPointerManager() {
    return ModulePointerManager.getInstance(myProject);
  }
}
