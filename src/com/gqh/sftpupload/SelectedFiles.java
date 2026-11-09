package com.gqh.sftpupload;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 从右键事件里解析要上传的文件。
 * <p>
 * 项目树 / 编辑器里是 VIRTUAL_FILE_ARRAY；「本地更改 / 提交」列表里拿到的是 CHANGES，
 * 需要自己转成 VirtualFile（删除的文件没有本地文件，跳过）。
 */
final class SelectedFiles {

    private SelectedFiles() {
    }

    static VirtualFile[] of(AnActionEvent e) {
        if (e == null) {
            return VirtualFile.EMPTY_ARRAY;
        }
        Set<VirtualFile> result = new LinkedHashSet<>();
        VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
        if (files != null) {
            for (VirtualFile f : files) {
                if (f != null) {
                    result.add(f);
                }
            }
        }
        if (result.isEmpty()) {
            Change[] changes = e.getData(VcsDataKeys.CHANGES);
            if (changes != null) {
                for (Change change : changes) {
                    if (change != null && change.getVirtualFile() != null) {
                        result.add(change.getVirtualFile());
                    }
                }
            }
        }
        return result.isEmpty() ? VirtualFile.EMPTY_ARRAY : result.toArray(VirtualFile.EMPTY_ARRAY);
    }
}
