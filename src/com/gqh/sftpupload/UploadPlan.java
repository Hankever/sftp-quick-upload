package com.gqh.sftpupload;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Stream;

/**
 * 把选中的文件/目录按「所在目录命中哪条映射」分组，算出每条映射实际要传哪些文件。
 * <p>
 * 一次多选里混着网页和资源目录的文件也没关系：分别扫描命中的映射目录，逐个文件按
 * 最长本地目录前缀归到最具体的映射，因此嵌套映射（resources 与 resources/config）
 * 也不会重复上传。
 */
final class UploadPlan {

    /** 扫描起点：localRoot 用来算相对路径，target 是真正要遍历的路径。 */
    static final class Match {
        final PathMapping mapping;
        final Path localRoot;
        final Path target;

        Match(PathMapping mapping, Path localRoot, Path target) {
            this.mapping = mapping;
            this.localRoot = localRoot;
            this.target = target;
        }
    }

    static final class Group {
        final PathMapping mapping;
        final Path localRoot;
        final LinkedHashSet<Path> files = new LinkedHashSet<>();

        Group(PathMapping mapping, Path localRoot) {
            this.mapping = mapping;
            this.localRoot = localRoot;
        }
    }

    private final Path projectBase;
    private final List<PathMapping> mappings;

    final LinkedHashMap<PathMapping, Group> groups = new LinkedHashMap<>();
    final List<Path> unmatched = new ArrayList<>();
    final List<String> walkErrors = new ArrayList<>();

    private UploadPlan(Path projectBase, List<PathMapping> mappings) {
        this.projectBase = projectBase;
        this.mappings = mappings;
    }

    int totalFiles() {
        int n = 0;
        for (Group g : groups.values()) {
            n += g.files.size();
        }
        return n;
    }

    /** 本地映射根：项目根 + localRoot，例如 /proj + src/main/resources。 */
    static Path resolveRoot(Path projectBase, PathMapping mapping) {
        String local = mapping.getLocalRoot().trim();
        if (local.isEmpty()) {
            return projectBase;
        }
        return projectBase.resolve(local).normalize();
    }

    /**
     * 一个选中项会涉及哪几条映射：
     * 在映射目录下面 → 取最具体（层级最深）的那条；
     * 选中的是映射目录的上级 → 取它下面所有「最外层」的映射目录。
     */
    static List<Match> match(Path projectBase, List<PathMapping> mappings, Path target) {
        Path p = target.normalize();
        Match best = null;
        List<Path> roots = new ArrayList<>();
        List<PathMapping> owners = new ArrayList<>();
        for (PathMapping mapping : mappings) {
            Path root = resolveRoot(projectBase, mapping);
            roots.add(root);
            owners.add(mapping);
            if (p.startsWith(root)) {
                if (best == null || root.getNameCount() > best.localRoot.getNameCount()) {
                    best = new Match(mapping, root, p);
                }
            }
        }

        List<Match> result = new ArrayList<>();
        if (best != null) {
            result.add(best);
            return result;
        }

        for (int i = 0; i < roots.size(); i++) {
            Path root = roots.get(i);
            if (!root.startsWith(p)) {
                continue;
            }
            boolean nested = false;
            for (int j = 0; j < roots.size(); j++) {
                Path other = roots.get(j);
                if (i != j && !other.equals(root) && root.startsWith(other)) {
                    nested = true;
                    break;
                }
            }
            if (!nested) {
                result.add(new Match(owners.get(i), root, root));
            }
        }
        return result;
    }

    /** 真正走一遍文件系统。 */
    static UploadPlan build(Path projectBase, List<PathMapping> mappings, List<Path> targets) {
        UploadPlan plan = new UploadPlan(projectBase, mappings);
        List<Path> scanRoots = new ArrayList<>();
        for (Path target : targets) {
            Path p = target.normalize();
            List<Match> matches = match(projectBase, mappings, p);
            if (matches.isEmpty()) {
                plan.unmatched.add(p);
                continue;
            }
            for (Match m : matches) {
                scanRoots.add(m.target);
            }
        }
        for (Path root : outermost(scanRoots)) {
            plan.scan(root);
        }
        return plan;
    }

    /**
     * 菜单预览用的轻量版本：不遍历目录，只按选中项本身算命中哪些映射。
     * key = 映射名，value = 命中的选中项个数。
     */
    static LinkedHashMap<String, Integer> preview(Path projectBase, List<PathMapping> mappings, List<Path> targets) {
        LinkedHashMap<String, Integer> result = new LinkedHashMap<>();
        for (Path target : targets) {
            for (Match m : match(projectBase, mappings, target.normalize())) {
                result.merge(m.mapping.getName(), 1, Integer::sum);
            }
        }
        return result;
    }

    /** 去掉被其他扫描起点包含的路径，避免同一目录走两遍。 */
    private static List<Path> outermost(List<Path> roots) {
        List<Path> out = new ArrayList<>();
        for (Path root : roots) {
            boolean nested = false;
            for (Path other : roots) {
                if (!other.equals(root) && root.startsWith(other)) {
                    nested = true;
                    break;
                }
            }
            if (!nested && !out.contains(root)) {
                out.add(root);
            }
        }
        return out;
    }

    private void scan(Path path) {
        if (!Files.exists(path)) {
            return;
        }
        if (Files.isDirectory(path)) {
            try (Stream<Path> walk = Files.walk(path)) {
                walk.filter(Files::isRegularFile).forEach(this::assign);
            } catch (IOException e) {
                walkErrors.add(path + "（" + e.getMessage() + "）");
            }
        } else {
            assign(path);
        }
    }

    /** 单个文件按最长本地目录前缀归到最具体的映射。 */
    private void assign(Path file) {
        PathMapping best = null;
        Path bestRoot = null;
        for (PathMapping mapping : mappings) {
            Path root = resolveRoot(projectBase, mapping);
            if (file.startsWith(root)
                    && (bestRoot == null || root.getNameCount() > bestRoot.getNameCount())) {
                best = mapping;
                bestRoot = root;
            }
        }
        if (best == null) {
            unmatched.add(file);
            return;
        }
        final PathMapping chosen = best;
        final Path chosenRoot = bestRoot;
        Group group = groups.computeIfAbsent(chosen, k -> new Group(k, chosenRoot));
        group.files.add(file);
    }
}
