<?php

namespace App\Http\Controllers;

use Illuminate\Http\JsonResponse;

class InfoController extends Controller
{
    public function __invoke(): JsonResponse
    {
        return response()->json([
            'app' => config('app.name'),
            'hostname' => gethostname(),
            'laravel' => app()->version(),
            'php' => PHP_VERSION,
            'miabi' => (object) config('app.miabi'),
        ]);
    }
}
