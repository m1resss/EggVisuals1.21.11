// Колыхание растительности от ветра Ambience — общий код вершинников чанков: ванильного (core/terrain.vsh)
// и Sodium (FoliageSway вписывает его в block_layer_opaque.vsh).
// Вес вершины приходит в альфе её цвета (FoliageModel): код = 255 - альфа; 1..11 — растение от корня (0)
// до макушки (1), 13 — листва, 14 — лианы; остальное не колышется.
// wind: xy — куда дует ветер (единичный вектор в мировых XZ), z — сила 0..1, w — время в секундах, свёрнутое
// в 1024 с, поэтому все частоты кратны 1/1024 Гц. world — мировая позиция вершины, свёрнутая в 4096 блоков.

const float EGG_SWAY_TAU = 6.2831853;

vec3 egg_foliageSway(vec3 world, float alpha, vec4 wind) {
    if (wind.z <= 0.0) return vec3(0.0);
    int code = int(floor((1.0 - alpha) * 255.0 + 0.5));
    float plant = (code >= 1 && code <= 11) ? float(code - 1) / 10.0 : 0.0;
    float leaves = code == 13 ? 1.0 : (code == 14 ? 0.8 : 0.0);
    if (plant <= 0.0 && leaves <= 0.0) return vec3(0.0);

    vec3 along = vec3(wind.x, 0.0, wind.y);
    vec3 across = vec3(-wind.y, 0.0, wind.x);
    // Порыв — волна, бегущая по ветру через поле; вторая, длинная, делает порывы неравномерными.
    float front = dot(world.xz, wind.xy);
    float gust = 0.5 + 0.5 * sin(front * 0.3 - wind.w * EGG_SWAY_TAU * 310.0 / 1024.0);
    gust *= 0.65 + 0.35 * sin(front * 0.07 + world.x * 0.021 - wind.w * EGG_SWAY_TAU * 98.0 / 1024.0);
    // Мелкая дрожь листьев и стеблей, у соседних блоков — в разной фазе.
    float flutter = sin(wind.w * EGG_SWAY_TAU * 1027.0 / 1024.0 + world.x * 1.7 + world.z * 1.3 + world.y * 0.9);
    float lean = wind.z * (0.3 + 0.7 * gust);

    vec3 plantSway = along * (0.25 * lean + 0.04 * flutter * wind.z) + across * (0.05 * flutter * wind.z);
    plantSway.y = -0.1 * lean * plant;
    vec3 leavesSway = along * (0.035 * lean) + (across * 0.025 + vec3(0.0, 0.02, 0.0)) * flutter * wind.z;
    return plantSway * plant + leavesSway * leaves;
}
