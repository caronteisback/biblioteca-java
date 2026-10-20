# Contributing

## Commit messages

This repository uses [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<optional scope>): <short summary in imperative mood>

<optional body: what and why>
```

Types: `feat`, `fix`, `docs`, `test`, `refactor`, `build`, `ci`, `chore`, `perf`.

Examples:

```
feat(prestamo): reject a loan when the copy is already on loan
fix(db): validate schema with ddl-auto=validate
docs: describe how to run the API against Oracle XE
```

Enable the commit template once per clone:

```bash
git config commit.template .gitmessage
```

## Branches

Work on short-lived branches (`feat/...`, `fix/...`) and merge into `main`.

## Before pushing

```bash
./mvnw test
```

Never commit `.env`, passwords, tokens or real data.
