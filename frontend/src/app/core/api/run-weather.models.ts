export type WeatherCondition = 'sun' | 'cloud' | 'rain';

/** The forecast for the 09:00-11:00 running window of one day (feature 018). */
export interface RunWeatherDay {
  /** ISO local date. */
  date: string;
  condition: WeatherCondition;
  /** Total mm in the window for `rain`, otherwise null. */
  rainMm: number | null;
}

export interface RunWeatherResponse {
  days: RunWeatherDay[];
}
