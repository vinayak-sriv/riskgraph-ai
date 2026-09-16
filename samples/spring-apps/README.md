# Sample Spring applications

RiskGraph uses authored Spring Boot repositories for reproducible demonstrations and
regression tests. Generate all four immutable commit pairs with:

```powershell
python tools/dev/create_mvp_samples.py
```

The generated scenarios cover:

- authorization removal;
- a safe cosmetic change;
- a new public sensitive endpoint; and
- sensitive-resource exposure through an existing public route.

Generated repositories are ignored because their Git commits are test artifacts.
They are analyzed as source only; RiskGraph never executes their build scripts.
Privilege-expansion and IDOR examples, where present, are design fixtures rather than
claims of implemented detection.
