---
name: codex-pr
description: タスク完了時にレビュー（CI Claude / CodeRabbit を主・Codex を最終1回）を前提とした PR を作成する標準ワークフロー。ブランチ作成 → コミット → push → gh pr create → 主レビュー自走ループ → Codex 最終確認までを規約に沿って実施する。Codex は枠節約のため毎 push では発火しない。
---

# Codex 向け PR 作成ワークフロー

本プロジェクトのコードレビューは **3 系統 + push 前 self-gate** で回す。**Codex は ChatGPT プラン側の利用枠を消費し、すぐ上限（"You have reached your Codex usage limits"）に達して無反応になる**ため、**枠を消費しない 2 系統（self-gate / CI Claude / CodeRabbit）を主レビューにし、Codex は「最終確認の 1 回」に節約して使う**運用とする：

1. **push 前セルフレビュー**（[self-review](../self-review/SKILL.md) スキル）— ローカルで自分の差分を別コンテキストに点検させ、明白なミスを PR 前に潰す。**無料・毎回必須**。
2. **CI 上の Claude（fresh context）＝主レビュー** — `.github/workflows/claude-review.yml` が `pull_request`（opened / synchronize / ready_for_review）契機で**毎 push 自動起動**し、実装の経緯を持たない別 Claude が diff だけを見てレビューを投稿する。サブスク枠（`CLAUDE_CODE_OAUTH_TOKEN`）で追加課金なし＝**反復レビューの軸はこれ**。
3. **CodeRabbit** — PR push を契機に自動レビュー（無料枠）。
4. **Codex GitHub App＝最終確認のみ** — PR に日本語レビューをコメント（`@codex review` で明示発火）。**枠が貴重なので毎 push では打たず、self-gate / CI Claude / CodeRabbit の反復が落ち着いた「最終 1 回」だけ**発火する。

本スキルはタスク完了時に上記がすぐ回る形で PR を作り、**主に CI Claude / CodeRabbit / self-gate で指摘を潰し切ってから、Codex を最終 1 回だけ回して仕上げる**標準手順。Codex が上限到達で無反応・エラー文言（usage limit）を返したら、それ以上 Codex を待たず CI Claude / CodeRabbit のクリーンを以て完了とする。

## 適用タイミング（When to Use）

- タスクの実装・ビルド検証・README 進捗反映・kaizen-close が完了し、PR にする段階
- ドキュメント・設定変更のみの場合は `task/<N>-...` ではなく `docs/<短い名>` ブランチで PR を作る運用も可
- 既存 PR への追加コミットではなく、**新規 PR を作る** とき

## 前提

- **ChatGPT Codex（Web 版）** を使用。chatgpt.com の Codex → Settings から `hang-up33/phantom_nexus` を GitHub OAuth 連携済み（GitHub の Installed Apps 一覧には "ChatGPT" / "OpenAI Codex" などの名前で出現する。マーケットプレイス検索ではヒットしない）
- Codex 側の自動レビュー設定が有効で、`ready-for-review` で PR を push すれば手動操作なしに Codex が PR コメントでレビューを返す
- `gh` CLI で認証済み（`gh auth status` で確認）
- リモート `origin` が GitHub の本リポジトリを指していること

CI Claude / CodeRabbit は PR を `ready-for-review` で push した時点で**自動でレビューを投稿**する。Claude 側は **PR をきれいに作り、これらの指摘を解消し切るところまで** が責任範囲。Codex は枠節約のため自動発火に任せず、最終確認のタイミングで**手動の `@codex review` を 1 回だけ**打つ。ユーザーが Codex に URL を貼る手動操作は不要。

## 規約

| 項目 | 値 |
|---|---|
| ブランチ名 | `task/<N>-<短い名>`（例：`task/2-login-form`） |
| PR タイトル | `<タスク識別子>: <短い説明>`（コミット先頭行と同じ） |
| PR 状態 | **ready-for-review**（draft にしない — CI Claude / CodeRabbit に即レビューさせるため） |
| マージ戦略 | Squash and merge（Claude はマージしない、ユーザーが行う） |

## 手順（How It Works）

### 1. 現在ブランチ確認

```sh
git branch --show-current
```

- `main` 上にいる場合、または別の `task/...` ブランチにいる場合は、新ブランチに切り替えが必要
- 既に該当 `task/<N>-...` ブランチにいるならスキップ

### 2. ブランチ作成（未作成なら）

```sh
git checkout main
git pull --ff-only
git checkout -b task/<N>-<短い名>
```

### 3. コミット

```sh
git add <変更ファイルを明示的に列挙>
git commit -m "$(cat <<'EOF'
<タスク識別子>: <短い説明>

<本文：何を、なぜ、どこに影響するか。1〜3 段落>

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

- `git add .` や `git add -A` は使わない（意図しないファイル混入防止）

### 3.5. push 前セルフレビュー（self-gate）

push の直前に [self-review](../self-review/SKILL.md) スキルを実行する。コミット済みの差分を
別コンテキスト（`/code-review` か Agent サブエージェント）に点検させ、確信のある小さな指摘は
その場で直してコミットに含め、保留した論点は PR 本文の `## セルフレビュー` 節に書く。

### 4. push

```sh
git push -u origin task/<N>-<短い名>
```

### 5. PR 作成

**HEREDOC は必ず `'EOF'`（quoted）で書き、本文中のシェル展開を完全に止める**。`${SHA}` のような可変値はプレースホルダで書いておき、後で `sed` 置換 → `--body-file` で渡す（PR 本文に `$VAR` / `$(...)` / バッククォートが混ざっても安全な構成）。

```sh
# GUI 変化がある場合は事前に push 済み SHA を控えておく（スクショ URL 用）
SHA=$(git rev-parse HEAD)

# 本文テンプレートをファイルに書き出す（HEREDOC 内は完全リテラル）
cat <<'EOF' | sed "s|__SHA__|${SHA}|g" > /tmp/pr_body.md
## Summary
- <変更点の箇条書き 1>
- <変更点の箇条書き 2>

## Screenshot
![<タスク識別子>: <短い説明>](https://raw.githubusercontent.com/hang-up33/phantom_nexus/__SHA__/docs/screenshots/<N>-<短い名>.png)

<スクショの簡単な説明：何が描画されている画面か>

## 設計書との対応
- <タスク識別子>: <タスク名>
- 完了基準: <ビルド成功・動作確認内容>

## Test plan
- [ ] `./gradlew build` 成功
- [ ] <UI 確認手順がある場合はそれ>
- [ ] README の進捗表が ✅ に更新されている

## セルフレビュー（push 前 self-gate）
- 修正済み: <その場で直した点／なければ「なし」>
- 残した論点: <保留した点と理由／なければ「なし」>

## Codex 向け補足
<重点的に見てほしい観点があれば箇条書きで。汎用的な「全体的に見てほしい」より、特定の観点を 1〜3 個提示>

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF

# --body-file で渡す（--body "$(...)" は使わない）
gh pr create --title "<タスク識別子>: <短い説明>" --body-file /tmp/pr_body.md
```

- `--draft` を **付けない**（Codex がレビュー対象として扱いやすい状態にする）
- `## Screenshot` セクションは **GUI 変化があるタスクで必須**。撮影方法は [CLAUDE.md](../../../CLAUDE.md)「動作証跡スクリーンショット運用」を参照（純粋なロジック / docs / 設定のみのタスクは省略可）。
- **`gh pr create --body "$(cat <<EOF ... EOF)"` で unquoted HEREDOC を使うのは禁止**。本文中の `$VAR` / `$(...)` / バッククォートが全てシェルに展開されて意図しないコマンド実行・本文破壊を招く。可変値は必ずプレースホルダ + 後置換で扱う。

### 6. 主レビュー（CI Claude / CodeRabbit）で先に収束させる ＝ Codex 枠を温存

**PR 作成直後に `@codex review` を打たない**。まずは**枠を消費しない主レビュー**で指摘を潰し切る：

- PR を push した時点で **CI Claude（`claude-review.yml`）と CodeRabbit が自動起動**する（`@codex review` は不要・毎 push 自動）。
- これらの指摘を取得（手順 7-A）→ **複数の指摘を 1 回の push にまとめて**修正（バッチ）→ push（自動で再レビューが走る）を、主レビューがクリーンになるまで繰り返す。
- **修正は必ずバッチ**：指摘 1 件ごとに push して都度レビューを呼ばない（CI Claude も無駄に何度も走り、後段の Codex 発火回数も増える）。関連する複数修正をまとめてから 1 回 push する。

### 7. Codex は「最終確認の 1 回」だけ発火する

self-gate 済み・**主レビュー（CI Claude / CodeRabbit）がクリーン**になったら、仕上げに **Codex を 1 回だけ**回す：

```sh
gh pr comment <PR番号> --body "@codex review"
```

- **毎 push では打たない**（枠浪費の最大要因）。主レビュー収束後の最終確認として 1 回だけ。
- Codex を取得（手順 7-A）して判定：
  - **usage limit エラー文言**（`"You have reached your Codex usage limits"`）が返った → **Codex はスキップ**。CI Claude / CodeRabbit のクリーンを以て完了報告へ（Codex を待たない・再依頼しない）。
  - **指摘あり** → **すべての Codex 指摘を 1 回の push にまとめて**修正 → `gh pr comment <N> --body "@codex review"` を**もう 1 回だけ**（最終）→ 取得して指摘 0 件を確認 → 完了。
  - **指摘なし**（クリーン文言）→ 完了。
- Codex 指摘対応で push すると CI Claude / CodeRabbit も再度走るので、Codex 再依頼の前にそれらのクリーンも確認する（追加の Codex 発火を避ける）。

#### 7-A. レビュー結果の取得・判定（CI Claude / CodeRabbit / Codex 共通）

レビュー結果を取得するときは、対象に応じて以下を使う。**ユーザーから再指示を待たず、Claude 側が自走でポーリングして指摘を解消し切る**（`gh pr create` 直後に完了報告して放置するのは禁止）。

取得は 3 種類の API（reviews / review comments / issue comments）を見る点はどのレビュアーでも共通で、**`select(.user.login == ...)` のボット名だけを切り替える**：

- **Codex**：`chatgpt-codex-connector[bot]`
- **CI Claude**：`github-actions[bot]`（`claude-review.yml` が `gh` で投稿）。本文に Claude のレビュー見出しが入る
- **CodeRabbit**：`coderabbitai[bot]`

主レビュー（CI Claude / CodeRabbit）は**毎 push 自動起動**なので `@codex review` のような発火コメントは不要。push 後にそのまま取得・判定する。

**ループ前の確定（毎ラウンド必須）**：CI Claude / CodeRabbit は **`git push` 直後**、Codex は **`gh pr comment <N> --body "@codex review"` を投稿し終えた直後**に、以降のラウンド全体で使う基準時刻を確定する：

```sh
SINCE=$(date -u +%Y-%m-%dT%H:%M:%SZ)   # 例: 2026-06-05T04:20:00Z
HEAD_SHA=$(git rev-parse HEAD)
```

`SINCE` を待機後に取得すると、待機中に Codex が投稿したクリーン文言を `?since=` で除外してしまい「シグナルなし」と誤判定して無反応扱いになる。**必ず待機の前に確定**する。

ループ手順：

1. **待機**：`sleep 180`〜`sleep 240`（Codex は通常 1〜5 分でレビューを返す）
2. **取得**：3 種類すべてを取る（Codex は指摘の有無で投稿先 API が変わる）。`SINCE` と `HEAD_SHA` はループ前に確定した値を使う。
   ```sh
   # 1) reviews API：指摘ありの review summary が来る（state / 本文）。commit_id で絞る
   gh api --paginate repos/hang-up33/phantom_nexus/pulls/<N>/reviews \
     --jq '.[] | select(.commit_id == "<HEAD-SHA>") | {state, body}'
   # 2) review comments API：inline コメント本体（指摘の中身）。
   #    **必ず original_commit_id で絞る（commit_id ではない）**。inline コメントの
   #    `commit_id` は、指摘行が新コミットでも存在し続けると GitHub に **最新 HEAD へ
   #    re-anchor（付け替え）される**ため、commit_id で絞ると前ラウンドの古い指摘が
   #    新 HEAD のものとして再マッチし「同じ指摘がまた来た」と誤検出する。
   #    `original_commit_id` は投稿時コミット固定なので、当該 HEAD への **新規** inline
   #    だけを正しく拾える。created_at >= SINCE の併用でさらに堅い。
   gh api --paginate repos/hang-up33/phantom_nexus/pulls/<N>/comments \
     --jq '.[] | select(.original_commit_id == "<HEAD-SHA>") | {path, line, body}'
   # 3) issue comments API：PR 会話タブのコメント。Codex は **指摘なしのときここに
   #    "Didn't find any major issues" を投稿する** ことが多く、reviews API に
   #    クリーン文言が載らないラウンドがあるため必ず併せて見る。
   #    **commit_id を持たないので必ず時刻で絞る**。過去ラウンドのクリーン文言を
   #    拾うと、最新 HEAD のレビューが未完了でもループを誤終了する。
   gh api --paginate "repos/hang-up33/phantom_nexus/issues/<N>/comments?since=${SINCE}" \
     --jq '.[] | select(.user.login == "chatgpt-codex-connector[bot]") | {created_at, body}'
   ```
   - `--paginate` を必ず付ける：gh api の per_page 既定値は 30 で、指摘が多い PR では先頭ページ以外を取りこぼし「指摘なし」と誤判定してループが早期終了する恐れがある
   - issue comments の `?since=...` は `updated_at >= since` のフィルタとして GitHub 側で適用される。`--jq 'select(.created_at >= "<SINCE>")'` で同等のクライアント側絞り込みも可（両方付けて二重防御するのが堅い）
   - 件数だけ確認したいときも `--jq 'length'` ではなく `... | wc -l` 等で全ページ通算する（`--jq 'length'` はページ単位の長さを各ページごとに出力する点に注意）
3. **判定**：以下を **state 優先** で評価する。「badge / 文言の有無」だけで判断すると、本文に badge が付かない `CHANGES_REQUESTED` を取りこぼしてループが早期終了する。
   - reviews API の `state` が `CHANGES_REQUESTED` → **必ず** 修正フロー (4) へ。badge や文言は見ない
   - `state` が `COMMENTED` でも review comments（inline）が当該 HEAD-SHA で 1 件以上ある → 修正フロー (4) へ
   - inline 0 件で、reviews 本文 / inline 本文に P0〜P3 / Major / Minor / `[Major]` / `[Minor]` 等の指摘 badge が含まれる → 修正フロー (4) へ
   - 上のいずれにも該当せず、かつ Codex が当該 HEAD 以降に issue comments / reviews のどちらかへ `"Didn't find any major issues"` 等のクリーン文言を投稿 → ループ脱出して完了報告
   - **どのシグナルも出ていない**（reviews も inline も issue comment もまだ無い）→ Codex がまだレビュー中。手順 1 の待機に戻る（ループは脱出しない）
4. **修正（必ずバッチ）**：そのラウンドの**指摘をまとめて**該当箇所を編集 → `./gradlew build` 成功確認 → 修正ファイルを明示的に `git add` → コミット（指摘 1 件ごとに push しない）
5. **push**：`git push`（force-push は基本しない、追加コミットで対応）。主レビュー（CI Claude / CodeRabbit）は自動で再起動する
6. **Codex のみ再依頼**：主レビュー対応の push では `@codex review` を打たない。**Codex 指摘対応の push 後に限り**、主レビューのクリーンを確認してから `gh pr comment <N> --body "@codex review"` を最終 1 回
7. **「ループ前の確定」に戻る** — `SINCE` と `HEAD_SHA` を **必ず再取得** してから手順 1 の待機へ。再取得しないと旧ラウンドの値で判定して誤動作する

ループ中の中間報告は不要（ユーザーは「次のレビュー結果が来るまでに何度修正したか」を後で PR の commit 履歴で確認できる）。

**自走を中断してユーザーに相談すべき例外**：

- 大規模な方針差し戻し（タスク分割を要求される、設計書外のフレームワーク導入を提案される 等）
- レビュアーが同じ指摘を 3 回連続で返してくる（修正が指摘の意図に合っていない可能性）
- ビルドが通らない / テストが赤いまま固着する
- 30 分待っても主レビュー（CI Claude / CodeRabbit）が無反応（CI 障害等の可能性 → ユーザー報告）

**Codex の usage limit は例外扱いにしない**：Codex が `"You have reached your Codex usage limits"` を返したら、**待たず・再依頼せず**にスキップし、CI Claude / CodeRabbit のクリーンを以て完了報告する（ユーザーへの再指示も不要）。完了報告に「Codex は枠上限でスキップ」と 1 行添える。

### 8. PR URL を完了報告に含める

クリーンレビュー到達後、`gh pr create` の戻り値の URL とともに、各レビュアーの収束状況をユーザーへの完了報告に含める（例：「CI Claude / CodeRabbit 指摘 2 件を 1 ラウンドで解消、Codex 最終確認：指摘なし」「Codex は枠上限でスキップ・主レビューはクリーン」）。

マージは従来通りユーザーが行う（Claude はマージしない）。

## やってはいけないこと（Must Never）

- `main` に直接 push / コミットする
- PR を draft で作る（CI Claude / CodeRabbit のレビュー開始が遅れる）
- **毎 push で `@codex review` を打つ**（Codex 枠浪費の最大要因。主レビューが収束してからの最終 1 回に限定する）
- **指摘 1 件ごとに push する**（修正はバッチでまとめて 1 push にする）
- Codex の usage limit を待ち続ける / 何度も再依頼する（スキップして主レビューのクリーンで完了する）
- PR を Claude 側でマージする（マージはユーザーの責任）
- `git add .` / `git add -A` で広く取り込む
- 既にレビュー中の PR を勝手に force-push して履歴を書き換える（追加コミットで対応する）
