import {HttpInterceptorFn, HttpErrorResponse} from '@angular/common/http';
import {catchError, throwError} from 'rxjs';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authedReq = req.clone({withCredentials: true});

  return next(authedReq).pipe(
    catchError((error: HttpErrorResponse) => {
      return throwError(() => error);
    })
  );
};
