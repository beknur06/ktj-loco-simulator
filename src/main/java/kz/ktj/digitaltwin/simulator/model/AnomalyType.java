package kz.ktj.digitaltwin.simulator.model;

/**
 * Типы аномалий, которые симулятор может инжектировать.
 * Каждая аномалия влияет на конкретные параметры.
 */
public enum AnomalyType {
    COOLANT_OVERHEAT,       // перегрев охлаждающей жидкости
    OIL_PRESSURE_DROP,      // падение давления масла
    BRAKE_PIPE_LEAK,        // утечка тормозной магистрали
    TRACTION_MOTOR_OVERHEAT,// перегрев обмоток ТЭД
    FUEL_LEAK,              // утечка топлива (ТЭ33А)
    CATENARY_VOLTAGE_SAG,   // просадка напряжения контактной сети (KZ8A)
    EXHAUST_TEMP_SPIKE,     // скачок температуры выхлопных газов (ТЭ33А)
    NONE                    // нет аномалии
}
