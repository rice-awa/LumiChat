### Task 15: 抽象世界、天气、出生点与状态效果差异

**Files:**
- Create: `src/main/java/com/riceawa/llm/compat/WorldTimeCompat.java`
- Create: `src/main/java/com/riceawa/llm/compat/WeatherCompat.java`
- Create: `src/main/java/com/riceawa/llm/compat/WorldInfoCompat.java`
- Create: `src/main/java/com/riceawa/llm/compat/MobEffectCompat.java`
- Modify: `src/main/java/com/riceawa/llm/util/EntityHelper.java`
- Modify: `src/main/java/com/riceawa/llm/function/impl/WorldInfoFunction.java`
- Modify: `src/main/java/com/riceawa/llm/function/impl/PlayerEffectsFunction.java`
- Modify: `src/main/java/com/riceawa/llm/function/impl/TimeControlFunction.java`
- Modify: `src/main/java/com/riceawa/llm/function/impl/WeatherControlFunction.java`

**Interfaces:**
- Produces: `WorldTimeCompat.getDayTime/setDayTime`。
- Produces: `WeatherCompat.setWeatherParameters`。
- Produces: `WorldInfoCompat.getBiomeId/getSpawnPosition/getMinimumBuildHeight/getSurfaceHeight`。
- Produces: `MobEffectCompat.getId/isBeneficial/getTranslationKey`。

- [ ] **Step 1: 查阅版本差异**

只读侦察核对 1.19、1.21.9、1.21.11、26.1/26.2 的 WorldClocks、WeatherData、respawn data、biome key 和 Holder<MobEffect>，写 `.superpowers/sdd/compat-world-effects.md`。

- [ ] **Step 2: 把 EntityHelper 的版本方法搬到 compat**

`EntityHelper` 保留稳定的 entity/server/world 获取，不再承载 day time/weather 条件编译。调用点改用新 compat。

- [ ] **Step 3: 迁移 WorldInfo 与 PlayerEffects**

所有 identifier/location、respawn data、minY/minBuildHeight、effect holder/value 条件块改为语义方法。业务层只处理展示字符串。

- [ ] **Step 4: 全业务层条件注释归零**

Run:

```bash
grep -R -n '//?' src/main/java/com/riceawa/llm/command src/main/java/com/riceawa/llm/function/impl src/main/java/com/riceawa/llm/template src/main/java/com/riceawa/llm/util
```

Expected: 无结果。若确有签名级差异无法抽象，先把相关代码整体搬到新的 compat 类，再重复检查；不得在业务目录保留例外。

- [ ] **Step 5: 多版本构建**

```bash
./gradlew :1.19:build :1.20.6:build :1.21.11:build :26.1:build :26.2:build
```

Expected: Java 25 环境 BUILD SUCCESSFUL。

- [ ] **Step 6: 提交**

```bash
git add \
  src/main/java/com/riceawa/llm/compat/WorldTimeCompat.java \
  src/main/java/com/riceawa/llm/compat/WeatherCompat.java \
  src/main/java/com/riceawa/llm/compat/WorldInfoCompat.java \
  src/main/java/com/riceawa/llm/compat/MobEffectCompat.java \
  src/main/java/com/riceawa/llm/util/EntityHelper.java \
  src/main/java/com/riceawa/llm/function/impl/WorldInfoFunction.java \
  src/main/java/com/riceawa/llm/function/impl/PlayerEffectsFunction.java \
  src/main/java/com/riceawa/llm/function/impl/TimeControlFunction.java \
  src/main/java/com/riceawa/llm/function/impl/WeatherControlFunction.java
git commit -m "refactor(compat): 收敛世界与状态效果版本差异"
```

---

