#!/usr/bin/env bash
#
# generate-changelog.sh — 按 Conventional Commits 规则解析 git log，生成中文变更日志。
#
# 用法:
#   ./generate-changelog.sh <since_ref> <head_ref>
#
# 参数:
#   since_ref (可选) — changelog 起始引用（tag/SHA/分支）。为空字符串时:
#                       1. 自动检测最近的 tag 作为起点
#                       2. 若无任何 tag，则取全部历史（首次发布场景）
#   head_ref  (可选) — changelog 终止引用，默认 HEAD
#
# 输出: Markdown 格式的中文变更日志到 stdout
#
# 设计说明:
#   - 使用 --no-merges 过滤合并提交（如 "Merge pull request #19" 这类噪声），
#     保留实际内容提交。squash-merged PR 的提交标题通常已含 "(#N)"，会原样保留。
#   - 按 Conventional Commits type 分组到中文段落；未识别 type 归入"其他变更"。
#   - 每条提交格式: "- <标题> (<sha7>)"
#   - 无变更时输出提示段落，避免空段。
#
set -euo pipefail

SINCE_REF="${1:-}"
HEAD_REF="${2:-HEAD}"

# ----------------------------------------------------------------------------
# 1. 确定日志范围 (since..head)
# ----------------------------------------------------------------------------
if [[ -n "$SINCE_REF" ]]; then
    RANGE="${SINCE_REF}..${HEAD_REF}"
else
    LATEST_TAG="$(git describe --tags --abbrev=0 2>/dev/null || true)"
    if [[ -n "$LATEST_TAG" ]]; then
        LATEST_TAG_SHA="$(git rev-parse "${LATEST_TAG}^{}" 2>/dev/null || true)"
        HEAD_SHA="$(git rev-parse "${HEAD_REF}^{}")"
        if [[ "$LATEST_TAG_SHA" == "$HEAD_SHA" ]]; then
            # 最新 tag 恰好指向 HEAD（手动打 tag 后触发工作流的场景），找倒数第二个 tag
            PREV_TAG="$(git describe --tags --abbrev=0 "${LATEST_TAG}^" 2>/dev/null || true)"
            if [[ -n "$PREV_TAG" ]]; then
                RANGE="${PREV_TAG}..${HEAD_REF}"
            else
                RANGE="${HEAD_REF}"
            fi
        else
            RANGE="${LATEST_TAG}..${HEAD_REF}"
        fi
    else
        RANGE="${HEAD_REF}"
    fi
fi

# ----------------------------------------------------------------------------
# 2. 按类型分组的中文标题映射
# ----------------------------------------------------------------------------
declare -A TYPE_TITLE
TYPE_TITLE["feat"]="新增功能"
TYPE_TITLE["fix"]="问题修复"
TYPE_TITLE["perf"]="性能优化"
TYPE_TITLE["refactor"]="重构"
TYPE_TITLE["docs"]="文档"
TYPE_TITLE["style"]="样式"
TYPE_TITLE["test"]="测试"
TYPE_TITLE["build"]="构建系统"
TYPE_TITLE["ci"]="持续集成"
TYPE_TITLE["chore"]="杂务"
TYPE_TITLE["revert"]="撤销"
TYPE_TITLE["__other__"]="其他变更"

# 分组输出顺序
TYPE_ORDER=(feat fix perf refactor docs style test build ci chore revert __other__)

# ----------------------------------------------------------------------------
# 3. 解析 git log，按 type 收集提交
#    git log 输出格式: "<完整标题><TAB><sha7>"
#    用 awk 拆解 Conventional Commits 前缀，输出 "<type><TAB><完整标题><TAB><sha7>"
# ----------------------------------------------------------------------------
declare -A ENTRIES
TOTAL_COUNT=0

# awk 拆解规则:
#   $0 = "feat(scope): 描述 (#19)\t<sha7>"  (TAB 是字段分隔符之一)
#   先按 TAB 分出 标题($1) 与 sha($2)
#   用 sub() 剥离 Conventional Commits 前缀，提取 type 并保留标题正文
#
# 注意: 不要写成 "git log ... || true | awk ..." —— shell 中 | 优先级高于 ||，
# 会被解析为 "(git log ...) || (true | awk ...)"，导致 awk 读取到空输入。
# 改用 set +e 临时关闭 errexit，或在管道前用 if 判断。这里用 if 判断更清晰。
parse_log() {
    local log_output
    if log_output="$(git log "$RANGE" --no-merges --format='%s%x09%h' 2>/dev/null)"; then
        printf '%s' "$log_output"
    fi
}

while IFS=$'\t' read -r type title sha; do
    [[ -z "$sha" ]] && continue
    type="${type,,}"
    type="${type// /}"
    if [[ -z "${TYPE_TITLE[$type]:-}" ]]; then
        type="__other__"
    fi
    ENTRIES["$type"]+="- ${title} (${sha})"$'\n'
    TOTAL_COUNT=$((TOTAL_COUNT + 1))
done < <(parse_log | awk -F'\t' '
    {
        raw_title = $1
        sha       = $2
        if (sha == "") next
        type = ""
        # 两阶段匹配 Conventional Commits，避免 gawk 对 \(\) 转义的怪异行为:
        #   阶段1: 提取开头字母序列作为候选 type
        #   阶段2: 验证剩余部分符合 "(scope)!?:" 或 "!?:" 前缀结构
        if (match(raw_title, /^[a-zA-Z]+/)) {
            candidate = substr(raw_title, 1, RLENGTH)
            rest = substr(raw_title, RLENGTH + 1)
            if (rest ~ /^[[:space:]]*(\([[:alnum:]._/-]+\))?!?:[[:space:]]/) {
                type = tolower(candidate)
            }
        }
        printf "%s\t%s\t%s\n", type, raw_title, sha
    }
')

# ----------------------------------------------------------------------------
# 4. 输出 Markdown
# ----------------------------------------------------------------------------
if [[ "$TOTAL_COUNT" -eq 0 ]]; then
    echo "本次发布无提交变更。"
    exit 0
fi

emit_group() {
    local type="$1"
    local title="${TYPE_TITLE[$type]}"
    local entries="${ENTRIES[$type]:-}"
    [[ -z "$entries" ]] && return 0
    printf '### %s\n\n' "$title"
    printf '%s' "$entries"
    printf '\n'
}

for type in "${TYPE_ORDER[@]}"; do
    emit_group "$type"
done

# vim: set ts=4 sw=4 et:
