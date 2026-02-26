# 任务清单: fix-mermaid-syntax

> **@status:** completed | 2026-02-26 12:38

```yaml
@feature: fix-mermaid-syntax
@type: implementation
@created: 2026-02-26 11:40
@status: completed
@mode: R3
@workflow: DELEGATED
@mermaid: 10.9.5
@scope: repo + .helloagents/**
```

<!-- LIVE_STATUS_BEGIN -->
状态: completed | 进度: 8/8 (100%) | 更新: 2026-02-26 12:37:41
当前: -
<!-- LIVE_STATUS_END -->

## 进度概览

| 完成 | 失败 | 跳过 | 总数 |
|------|------|------|------|
| 7 | 0 | 1 | 8 |

---

## 任务列表

### 1) 准备与基线

- [√] MERM-01 盘点全仓库 Mermaid 代码块清单（含隐藏目录）  
      参考命令：
      ```bash
      rg --hidden -uuu -n '```mermaid' .
      ```
      产出：文件路径 + Mermaid 块起始行（与 proposal.md“影响范围”对齐）

- [√] MERM-02 搭建临时 Mermaid v10.9.5 parse-only 校验环境（不入库）  
      建议目录：`/tmp/linkforge-mermaid-validate/`  
      建议命令：
      ```bash
      npm init -y
      npm i mermaid@10.9.5 jsdom
      ```
      产出：`validate-mermaid.mjs`（示例脚本如下，可直接用）
      ```js
      // validate-mermaid.mjs
      import fs from "node:fs/promises";
      import path from "node:path";
      import createDOMPurify from "dompurify";
      import { JSDOM } from "jsdom";

      const { window } = new JSDOM("");
      const dompurifyInstance = createDOMPurify(window);
      for (const method of ["sanitize", "addHook", "removeHook", "isSupported"]) {
        if (typeof dompurifyInstance[method] === "function") {
          createDOMPurify[method] = dompurifyInstance[method].bind(dompurifyInstance);
        }
      }

      const { default: mermaid } = await import("mermaid");

      const root = path.resolve(process.argv[2] ?? ".");
      const ignoreDirs = new Set([".git", "node_modules", "target", "dist"]);

      async function* walk(dir) {
        const entries = await fs.readdir(dir, { withFileTypes: true });
        for (const entry of entries) {
          if (entry.isDirectory() && ignoreDirs.has(entry.name)) continue;
          const full = path.join(dir, entry.name);
          if (entry.isDirectory()) yield* walk(full);
          else if (entry.isFile() && entry.name.endsWith(".md")) yield full;
        }
      }

      const fenceRe = /```mermaid\\s*\\r?\\n([\\s\\S]*?)\\r?\\n```/g;

      let blocks = 0;
      let errors = 0;

      for await (const file of walk(root)) {
        const text = await fs.readFile(file, "utf8");
        let match;
        let idx = 0;
        while ((match = fenceRe.exec(text)) !== null) {
          idx += 1;
          blocks += 1;
          const code = match[1].trimEnd();
          try {
            await mermaid.parse(code);
          } catch (e) {
            errors += 1;
            const msg = e?.message ? String(e.message).split("\\n")[0] : String(e);
            console.error(`${file}:${idx}: ${msg}`);
          }
        }
      }

      console.log(`Mermaid blocks: ${blocks}, errors: ${errors}`);
      process.exit(errors === 0 ? 0 : 1);
      ```
      运行方式：
      ```bash
      node validate-mermaid.mjs "/home/feng/code/project/LinkForge"
      ```

- [√] MERM-03 执行一次基线校验并记录失败点  
      产出：失败列表（至少包含 file + block index + message），填入“执行日志”备注列

### 2) 语法修复（按风险点优先）

- [√] MERM-04 修复 `.helloagents/wiki/arch.md` 中虚线边带文字写法（#L23-24 附近）  
      目标：`Redis -.可选扩展.-> MQ` / `MQ -.可选扩展.-> OLAP` 在 v10.9.5 下可解析  
      建议改法：`Redis -. 可选扩展 .-> MQ`（或 parse 通过的等价写法）

- [-] MERM-05 修复 `.helloagents/archive/2026-02/202602242331_package-ownership-refactor/proposal.md` 子图连接（#L112-130 附近）  
      目标：避免 `API --> Shared` / `Edge --> Shared` 直接指向 subgraph id  
      建议改法：改为连接到 Shared 子图内的显式节点（或引入锚点节点后连接锚点）

- [√] MERM-06 修复 `.helloagents/archive/2026-02/202602252042_shortlink-core-gap-analysis/proposal.md` 节点 label 换行（#L124-126 附近）  
      目标：将 `\\n` 统一为 `<br/>`（与仓库其它图一致），并确保 parse 通过  
      示例：`D[Domain Resolver\\n(cache)]` → `D[Domain Resolver<br/>(cache)]`

- [√] MERM-07 回归其余 Mermaid 代码块（history/*、plan/* 等）  
      若 MERM-03 或后续校验发现新错误：按“最小语法修复”原则修复，并在执行日志备注中记录具体文件与修复点

### 3) 验收

- [√] MERM-08 终验：再次运行 parse-only 校验，确保全仓库 Mermaid 语法错误为 0  
      验收口径：`mermaid@10.9.5` + `mermaid.parse()` 全部通过  
      产出：在执行日志记录终验时间与结果（0 error）

---

## 执行日志

| 时间 | 任务 | 状态 | 备注 |
|------|------|------|------|
| 2026-02-26 11:57 | MERM-01 | completed | 盘点完成：共 9 个 Mermaid fenced block（7 个文件）；`rg` 输出包含 tasks.md 的字符串命中但不影响 parse-only 校验 |
| 2026-02-26 11:57 | MERM-02 | completed | 已创建 `/tmp/linkforge-mermaid-validate/` 并安装 `mermaid@10.9.5` + `jsdom`；写入 `validate-mermaid.mjs`（parse-only，含 DOMPurify patch） |
| 2026-02-26 12:05 | MERM-03 | completed | 基线 parse-only 失败：9 blocks 中 6 blocks 报 “Parse error”；主要触发点为 `{code}` 花括号与括号等特殊字符在 flowchart label 中的分词/语法冲突 |
| 2026-02-26 12:12 | MERM-04 | completed | 修复 `.helloagents/wiki/arch.md`：将 `GET /r/{code}` 改为 `GET /r/:code`；虚线边文本写法改为 `-. 可选扩展 .->` |
| 2026-02-26 12:13 | MERM-05 | skipped | v10.9.5 parse-only 校验通过；未发现语法错误点，避免为“纯风格优化”引入无必要变更 |
| 2026-02-26 12:17 | MERM-06 | completed | 修复 gap-analysis flowchart：`/r/{code}`→`/r/:code`；`\\n`→`<br/>`；含括号的 node label 改为引号形式 `D[\"...\"]`/`S[\"...\"]` |
| 2026-02-26 12:36 | MERM-07 | completed | 修复剩余 4 个 flowchart：统一 `/r/{code}`→`/r/:code`；括号 label/edge label 按需引号化；回归校验达到 `errors: 0` |
| 2026-02-26 12:37 | MERM-08 | completed | 终验通过：`Mermaid blocks: 9, errors: 0` |

---

## 执行备注

- 校验脚本与 npm 依赖仅在临时目录使用，不提交入库。
- 修复原则：优先语法兼容，其次保持图意不变；若必须引入锚点节点，命名保持中性并尽量不影响阅读。
- 以 Mermaid v10.9.5 parse-only 作为硬验收口径；如仍有渲染争议，再做抽样渲染复核。
