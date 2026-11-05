# Task: open ONE pull request that adds Hermes Ark to the Hermes plugin catalog

You are controlling a web browser that is already signed in to GitHub as **medomohamed22**, the owner of the
plugin repository https://github.com/medomohamed22/hermes-ark. Everything you need is online. Open each link in a
browser tab; the raw links show plain text you can select and copy:

| What | Link |
|---|---|
| The catalog entry, the ONLY file the PR adds | https://raw.githubusercontent.com/medomohamed22/hermes-ark/ccr-48a5f4a9-dk089c/submission/hermes-ark.yaml |
| The pull request description; paste it exactly | https://raw.githubusercontent.com/medomohamed22/hermes-ark/ccr-48a5f4a9-dk089c/submission/hermes-ark-PR.md |
| The one-click "new file" link | https://raw.githubusercontent.com/medomohamed22/hermes-ark/ccr-48a5f4a9-dk089c/submission/pr-link.txt (open it, copy the whole single line, open that URL) |

Below, "", "" and "" mean the content at these links.

Goal: a pull request from `medomohamed22`'s fork into **`NousResearch/hermes-agent`, branch `main`**, that adds one
file, `plugin-catalog/hermes-ark.yaml`, titled `feat(plugin-catalog): add hermes-ark plugin`.

## Rules — read before doing anything

1. **Do not change the content of `hermes-ark.yaml`.** Especially not the `sha:` line
   (`f2d5378724134dd62010df4def48ad2e0dad761e`), and not the indentation. YAML breaks if spaces change.
2. Add **exactly one file**: `plugin-catalog/hermes-ark.yaml`. Do not edit, delete or add any other file.
3. Do not open more than one pull request. Before creating it, check that no pull request titled
   `feat(plugin-catalog): add hermes-ark plugin` by medomohamed22 is already open in NousResearch/hermes-agent.
   If one exists, stop and report its link.
4. Do not merge, close, approve, or request reviewers. Do not change any repository settings.
   Do not touch https://github.com/medomohamed22/hermes-ark itself.
5. If GitHub asks for a password, two-factor code, or any permission you were not told about, **stop and ask the user.**
   Never type credentials yourself.
6. If something looks different from these steps, stop and describe what you see instead of improvising.

## Steps (Path A: the one-click link)

1. Confirm you are signed in as **medomohamed22**: the avatar at the top right, or visit https://github.com/settings/profile.
2. Open the full link from `pr-link.txt`. It is long; copy all of it, starting with
   `https://github.com/NousResearch/hermes-agent/new/main?filename=...`.
3. You should see GitHub's editor on **NousResearch/hermes-agent**, usually with a notice like
   *"You're making changes in a project you don't have write access to… a fork will be created"*.
   Check that:
   - the file name box shows `plugin-catalog/` / `hermes-ark.yaml`;
   - the editor content starts with `name: hermes-ark` and contains
     `sha: f2d5378724134dd62010df4def48ad2e0dad761e`;
   - the content ends with the `requires_env:` list (`ARK_PASSPHRASE`, `ARK_S3_ACCESS_KEY_ID`, `ARK_S3_SECRET_ACCESS_KEY`).

   If the editor is empty or the content is cut off, use Path B instead.
4. Click **Commit changes…** (or **Propose changes**). In the dialog:
   - Commit message: `feat(plugin-catalog): add hermes-ark plugin`
   - Extended description: leave empty
   - If asked, choose *"Create a new branch for this commit and start a pull request"*. Any branch name is fine,
     e.g. `add-hermes-ark`.
   - Click **Propose changes**.
5. GitHub opens the *"Comparing changes"* page. Check:
   - base repository: **NousResearch/hermes-agent**, base: **main**;
   - head repository: **medomohamed22/hermes-agent**, compare: the new branch;
   - "Showing 1 changed file", with `plugin-catalog/hermes-ark.yaml` as an added file.
6. Click **Create pull request**. Then:
   - **Title:** `feat(plugin-catalog): add hermes-ark plugin`
   - **Description:** select all the text already in the box and delete it, then paste the full content of
     `hermes-ark-PR.md`, from `## What does this PR do?` to the last screenshot line.
   - Leave **"Allow edits by maintainers"** checked.
   - Click **Create pull request**. Not "Create draft pull request".
7. On the new pull request page, check:
   - the title is right;
   - the *Files changed* tab shows exactly 1 file;
   - the description shows the three screenshots.

## Path B: if the link does not work

1. Go to https://github.com/NousResearch/hermes-agent and click **Fork**, then **Create fork**.
   Keep the name `hermes-agent` and the owner `medomohamed22`. If a fork already exists, open it.
2. In the fork (https://github.com/medomohamed22/hermes-agent), first make sure it is up to date. If the fork page
   says *"This branch is N commits behind"*, click **Sync fork → Update branch**.
3. Create a branch: open the branch dropdown, type `add-hermes-ark`, and click *Create branch: add-hermes-ark from main*.
4. On that branch go to the `plugin-catalog` folder, then **Add file → Create new file**.
   File name: `hermes-ark.yaml`. Paste the full content of the `hermes-ark.yaml` file exactly as given.
5. **Commit changes…** with the message `feat(plugin-catalog): add hermes-ark plugin`, committed directly to `add-hermes-ark`.
6. Click **Contribute → Open pull request**. Make sure base is **NousResearch/hermes-agent : main** and 1 file changed,
   then continue from step 6 of Path A.

## After the pull request exists

1. Report back to the user:
   - the pull request URL (e.g. `https://github.com/NousResearch/hermes-agent/pull/12345`);
   - the number of changed files, which must be 1;
   - anything unusual you saw.
2. Open the PR's **Checks** tab and report what the checks show after a few minutes, especially
   **plugin-catalog-ci** (structural validation and pinned-source validation). Do not try to fix anything
   yourself; just report the status and any error text.
3. Do not post comments on the pull request unless the user asks.
