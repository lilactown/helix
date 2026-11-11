# AI Policy

This doc aims to describe the methods and policies that I (lilactown) use when
developing this repo (helix) with AI tools (LLMs).

First, I do not use LLMs for writing prose. All documents (e.g. README.md,
CHANGELOG.md, docs/) are written by me.

Second, this codebase has as much utility for me in a didactic sense as it does
as a library. I learn so much by figuring out how to express the solution to
problems I run into while doing web development in ClojureScript. I am unwilling
to let go of those learnings. At the same time, LLMs can find many bugs and
issues with my approaches and have unlimited stamina (up to my plan limits 😅).

Third, there are no productivity requirements for helix; helix is already fairly
effective at achieving its goal of providing a simple, easy to use library for
React development in CLJS. Improvements to it are necessary only when changes to
React itself require them, which is not often. I imagine improvements are
appreciated in as much as they actually improve the day-to-day work of an
engineer building a product. Therefore new features, bugs fixes, etc. are
weighed heavily against the cost of any changes that engineers will need to make
and the complexity it introduces to the library internals.

## When I use AI tools for code review...
0. I may use AI tools for reviewing the whole codebase or reviewing specific
   commits/PRs, written by myself or others.
1. I read the feedback provided to me and independently assess it using tests,
   demos, and (if within reason) reproducing issues in projects.
2. I do not create github issues or comments automatically; instead, I use my
   independent assessment to create issues or comments on existing issues in my
   own words.

## When I use AI tools for code generation...
0. I may ask AI tools to generate changes to the codebase, either in an agentic
   way (e.g. Claude Code, codex, etc.) or via fancy autocomplete (copilot,
   etc.).
1. I am obligated to understand every change that is committed and released to
   the public. I am responsible for every line of code in this repository.
2. I will personally review all code that is committed line by line. I provide
   feedback to AI tools based on my sensibilities and good judgement. I may take
   a change written by AI and commit it with no changes, or completely rewrite
   it by hand, or something in the middle.
3. I will ensure all commits are noted as authored by me (i.e. no
   `Co-Authored-By` with AI bots in it).

## When assessing external contributions written using AI tools...
0. I will reject any changes to the codebase that I do not understand. This may
   mean that I take a change proposed by an external contributor using AI and
   rewrite it in my own way.
1. I will reject completely automated (i.e. bot generated) PRs or issues,
   outside of known & approved bots for security patches.
2. I will reject a PR or issue description if it is clearly AI-generated with a
   request to rewrite it in the author's own words.
3. I will reject changes and issues from users who consistently reply with AI
   generated prose. I want to collaborate with humans, not chatbots.
4. I will reject commits that show they are authored by claude code, codex,
   etc. Code should be attributed to the person submitting the change.
