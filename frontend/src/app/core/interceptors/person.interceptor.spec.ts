import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { PersonStore } from '../person/person.store';
import { personInterceptor } from './person.interceptor';

describe('personInterceptor', () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([personInterceptor])),
        provideHttpClientTesting(),
      ],
    });
  });
  afterEach(() => localStorage.clear());

  it('R3 - adds X-Person to api requests only', () => {
    const http = TestBed.inject(HttpClient);
    const controller = TestBed.inject(HttpTestingController);
    TestBed.inject(PersonStore).select('marina');

    http.get('/api/sync').subscribe();
    http.get('/auth/other').subscribe();

    expect(controller.expectOne('/api/sync').request.headers.get('X-Person')).toBe('marina');
    expect(controller.expectOne('/auth/other').request.headers.has('X-Person')).toBe(false);
  });

  it('R16 - an unflipped browser asks for ricardo', () => {
    TestBed.inject(HttpClient).get('/api/sync').subscribe();
    const request = TestBed.inject(HttpTestingController).expectOne('/api/sync').request;
    expect(request.headers.get('X-Person')).toBe('ricardo');
  });
});
