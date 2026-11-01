import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { MarathonPaceResponse } from './marathon-pace.models';

@Injectable({ providedIn: 'root' })
export class MarathonPaceService {
  private readonly http = inject(HttpClient);

  getMarathonPace(): Observable<MarathonPaceResponse> {
    return this.http.get<MarathonPaceResponse>('/api/marathon-pace');
  }
}
