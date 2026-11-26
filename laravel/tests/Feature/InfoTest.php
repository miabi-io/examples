<?php

namespace Tests\Feature;

use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

class InfoTest extends TestCase
{
    use RefreshDatabase;

    private const ENV = ['MIABI_RELEASE' => '7', 'MIABI_NODE' => 'worker-2', 'MIABI_SECRET_TOKEN' => 'x'];

    protected function setUp(): void
    {
        // Config reads the environment when the application boots, so it is set before parent::setUp().
        foreach (self::ENV as $key => $value) {
            $_SERVER[$key] = $_ENV[$key] = $value;
        }

        parent::setUp();
    }

    protected function tearDown(): void
    {
        parent::tearDown();

        foreach (array_keys(self::ENV) as $key) {
            unset($_SERVER[$key], $_ENV[$key]);
        }
    }

    public function test_info_lists_only_allowed_miabi_variables(): void
    {
        $this->getJson('/api/info')
            ->assertOk()
            ->assertJsonPath('miabi.MIABI_RELEASE', '7')
            ->assertJsonMissingPath('miabi.MIABI_SECRET_TOKEN');
    }

    public function test_home_page_names_the_release_and_node(): void
    {
        $this->get('/')->assertOk()->assertSee('Release 7 on node worker-2');
    }
}
