import { Pipe, PipeTransform } from '@angular/core';
import { formatPace } from '../format/pace';

/** 253 → "4:13 /km". */
@Pipe({ name: 'pace' })
export class PacePipe implements PipeTransform {
  transform(secondsPerKm: number): string {
    return `${formatPace(secondsPerKm)} /km`;
  }
}
